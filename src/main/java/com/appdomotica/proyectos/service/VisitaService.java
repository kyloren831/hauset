package com.appdomotica.proyectos.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.CruceAgendaException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.repository.ChecklistTareaRepository;
import com.appdomotica.proyectos.repository.ProyectoEventoRepository;
import com.appdomotica.proyectos.repository.VisitaRepository;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Visitas del proyecto (RF-PRY-11/12, RN-09). El que llama ya bloqueó la fila del proyecto y abrió la
 * transacción; aquí solo están las reglas.
 *
 * - Una sola visita en curso por proyecto. Iniciar cuando ya hay una en curso de hoy no hace nada (así,
 *   reenviar desde un teléfono sin señal es inofensivo); una en curso de otro día se da por terminada.
 * - Completar una tarea sin visita en curso la inicia: el avance siempre queda en alguna visita.
 * - Iniciar toma la visita agendada para hoy o el retorno pendiente; si no hay, crea una nueva.
 */
@Service
@RequiredArgsConstructor
public class VisitaService {
    static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", Locale.US);
    private static final Set<EstadoVisita> POR_VENIR = EnumSet.of(EstadoVisita.PENDIENTE_AGENDAR, EstadoVisita.AGENDADA);
    private static final Set<RolUsuario> ASIGNABLES = EnumSet.of(RolUsuario.INSTALADOR, RolUsuario.JEFE_SISTEMAS,
            RolUsuario.JEFE_ELECTROMECANICO, RolUsuario.INGENIERO_INDUSTRIAL);
    private static final int DIAS_ATRAS = 30;
    /** Duración de una visita si quien agenda no dice otra (minutos). */
    public static final int DURACION = 120;
    private static final int DURACION_MAXIMA = 720;

    private final VisitaRepository visitas;
    private final ChecklistTareaRepository tareas;
    private final ProyectoEventoRepository eventos;
    private final UsuarioRepository usuarios;

    /**
     * Cuándo pasó algo hecho en el teléfono, quizá sin señal. Se acepta si no está en el futuro ni tiene más
     * de 30 días; si no, cuenta la hora del servidor.
     */
    public static OffsetDateTime momento(OffsetDateTime declarado) {
        OffsetDateTime ahora = OffsetDateTime.now();
        if (declarado == null || declarado.isAfter(ahora) || declarado.isBefore(ahora.minusDays(DIAS_ATRAS))) {
            return ahora;
        }
        return declarado;
    }

    /** Visita en curso; si no hay (o es de otro día), inicia una. */
    public Visita iniciar(Proyecto p, Usuario u, OffsetDateTime cuando) {
        Optional<Visita> enCurso = visitas.findFirstByProyectoIdAndEstado(p.getId(), EstadoVisita.EN_CURSO);
        if (enCurso.isPresent()) {
            if (dia(enCurso.get().getIniciadaEn()).equals(dia(cuando))) {
                return enCurso.get();
            }
            terminarSola(p, enCurso.get());
        }
        Visita v = candidata(p, u, cuando).orElseGet(() -> Visita.builder()
                .proyecto(p)
                .numeroVisita(visitas.ultimoNumero(p.getId()) + 1)
                .build());
        v.setEstado(EstadoVisita.EN_CURSO);
        v.setIniciadaEn(cuando);
        v.setIniciadaPor(u);
        if (v.getAsignadoA() == null) {
            v.setAsignadoA(u);
        }
        if (v.getFechaHora() == null) {
            v.setFechaHora(cuando);
        }
        v.setUpdatedAt(OffsetDateTime.now());
        // La entidad nace con version = 1, así que Spring la "fusiona": hay que usar la copia que devuelve.
        v = visitas.saveAndFlush(v);
        eventos.save(new ProyectoEvento(p, "VISITA_INICIADA", u, "Visita " + v.getNumeroVisita()));

        if (p.getEstado() == EstadoProyecto.COTIZADO || p.getEstado() == EstadoProyecto.REQUIERE_RETORNO) {
            String antes = etiqueta(p.getEstado());
            cambiarEstado(p, EstadoProyecto.EN_INSTALACION, u, antes);
        }
        return v;
    }

    /**
     * La agendada para hoy (primero la de esta persona); si el proyecto espera un retorno, ese retorno aunque
     * esté agendado para otro día o todavía sin fecha (el equipo llegó antes).
     */
    private Optional<Visita> candidata(Proyecto p, Usuario u, OffsetDateTime cuando) {
        List<Visita> porVenir = visitas.findByProyectoIdAndEstadoIn(p.getId(), POR_VENIR);
        Optional<Visita> deHoy = porVenir.stream()
                .filter(v -> v.getEstado() == EstadoVisita.AGENDADA && dia(v.getFechaHora()).equals(dia(cuando)))
                .min(Comparator.comparing((Visita v) -> v.getAsignadoA() == null
                                || !v.getAsignadoA().getId().equals(u.getId()))
                        .thenComparing(Visita::getFechaHora));
        if (deHoy.isPresent()) {
            return deHoy;
        }
        return porVenir.stream()
                .filter(v -> p.getEstado() == EstadoProyecto.REQUIERE_RETORNO
                        || v.getEstado() == EstadoVisita.PENDIENTE_AGENDAR)
                .min(Comparator.comparing(Visita::getNumeroVisita));
    }

    /** Termina la visita en curso, si hay. */
    public void terminar(Proyecto p, Usuario u, OffsetDateTime cuando) {
        visitas.findFirstByProyectoIdAndEstado(p.getId(), EstadoVisita.EN_CURSO).ifPresent(v -> {
            cerrarVisita(v, EstadoVisita.COMPLETADA, u, cuando);
            eventos.save(new ProyectoEvento(p, "VISITA_TERMINADA", u, "Visita " + v.getNumeroVisita()));
        });
    }

    /**
     * El instalador marca que hay que volver (RF-PRY-12): la visita en curso termina con el motivo y queda una
     * visita nueva esperando que un jefe o el ingeniero industrial le asigne fecha y persona (RN-09, RF-AGE-04).
     */
    public void requiereRetorno(Proyecto p, Usuario u, String motivo, OffsetDateTime cuando) {
        if (p.getEstado() == EstadoProyecto.REQUIERE_RETORNO) {
            throw new ConflictException("Ya está marcado que el proyecto requiere retorno");
        }
        String m = motivo.trim();
        visitas.findFirstByProyectoIdAndEstado(p.getId(), EstadoVisita.EN_CURSO).ifPresent(v -> {
            v.setMotivoRetorno(m);
            cerrarVisita(v, EstadoVisita.REQUIERE_RETORNO, u, cuando);
        });
        Visita retorno = Visita.builder()
                .proyecto(p)
                .numeroVisita(visitas.ultimoNumero(p.getId()) + 1)
                .estado(EstadoVisita.PENDIENTE_AGENDAR)
                .motivo(m)
                .build();
        visitas.saveAndFlush(retorno);
        eventos.save(new ProyectoEvento(p, "RETORNO", u, m));
        cambiarEstado(p, EstadoProyecto.REQUIERE_RETORNO, u, etiqueta(p.getEstado()));
    }

    /**
     * Agenda una visita nueva (jefes e ingeniero industrial, RF-AGE-02). Si la persona ya tiene otra visita en
     * ese horario avisa con {@link CruceAgendaException}, salvo que quien agenda lo confirme (RF-AGE-03).
     */
    public Visita agendar(Proyecto p, Usuario quien, UUID asignadoAId, OffsetDateTime fechaHora, String motivo,
                          int duracionMinutos, boolean confirmarCruce) {
        Usuario asignado = asignable(asignadoAId);
        validarFecha(fechaHora);
        if (!confirmarCruce) {
            avisarCruces(asignado, fechaHora, duracionMinutos, null);
        }
        Visita v = Visita.builder()
                .proyecto(p)
                .numeroVisita(visitas.ultimoNumero(p.getId()) + 1)
                .estado(EstadoVisita.AGENDADA)
                .asignadoA(asignado)
                .agendadoPor(quien)
                .fechaHora(fechaHora)
                .duracionMinutos(duracionMinutos)
                .motivo(motivo == null || motivo.isBlank() ? null : motivo.trim())
                .build();
        v = visitas.saveAndFlush(v);
        eventos.save(new ProyectoEvento(p, "VISITA_AGENDADA", quien, descripcionAgenda(v)));
        return v;
    }

    /**
     * Asigna fecha y persona a un retorno, o mueve una visita agendada. `duracionMinutos` null conserva la que
     * tenía. Los cruces se revisan contra la persona nueva y sin contar la propia visita.
     */
    public void reagendar(Visita v, Long version, Usuario quien, UUID asignadoAId, OffsetDateTime fechaHora,
                          Integer duracionMinutos, boolean confirmarCruce) {
        verificarVersion(v, version);
        if (!POR_VENIR.contains(v.getEstado())) {
            throw new ConflictException("Solo se agenda una visita que todavía no empieza");
        }
        validarFecha(fechaHora);
        Usuario asignado = asignable(asignadoAId);
        int duracion = duracionMinutos != null ? duracionMinutos : v.getDuracionMinutos();
        if (!confirmarCruce) {
            avisarCruces(asignado, fechaHora, duracion, v.getId());
        }
        v.setAsignadoA(asignado);
        v.setFechaHora(fechaHora);
        v.setDuracionMinutos(duracion);
        v.setAgendadoPor(quien);
        v.setEstado(EstadoVisita.AGENDADA);
        v.setUpdatedAt(OffsetDateTime.now());
        visitas.saveAndFlush(v);
        eventos.save(new ProyectoEvento(v.getProyecto(), "VISITA_AGENDADA", quien, descripcionAgenda(v)));
    }

    /** Cancela una visita que no ha empezado. Si era el último retorno pendiente, el proyecto vuelve a instalación. */
    public void cancelar(Visita v, Long version, Usuario quien) {
        verificarVersion(v, version);
        if (!POR_VENIR.contains(v.getEstado())) {
            throw new ConflictException("Solo se cancela una visita que todavía no empieza");
        }
        Proyecto p = v.getProyecto();
        v.setEstado(EstadoVisita.CANCELADA);
        v.setUpdatedAt(OffsetDateTime.now());
        visitas.saveAndFlush(v);
        eventos.save(new ProyectoEvento(p, "VISITA_CANCELADA", quien, "Visita " + v.getNumeroVisita()));
        if (p.getEstado() == EstadoProyecto.REQUIERE_RETORNO
                && visitas.findByProyectoIdAndEstadoIn(p.getId(), POR_VENIR).isEmpty()) {
            cambiarEstado(p, EstadoProyecto.EN_INSTALACION, quien, etiqueta(p.getEstado()));
        }
    }

    /** Al cerrar el proyecto: las visitas que no empezaron ya no se harán. */
    public void cancelarPorVenir(Proyecto p) {
        for (Visita v : visitas.findByProyectoIdAndEstadoIn(p.getId(), POR_VENIR)) {
            v.setEstado(EstadoVisita.CANCELADA);
            v.setUpdatedAt(OffsetDateTime.now());
            visitas.save(v);
        }
    }

    public void cerrarVisita(Visita v, EstadoVisita fin, Usuario u, OffsetDateTime cuando) {
        v.setEstado(fin);
        // Si el teléfono declara un fin anterior al inicio (relojes distintos), se toma el inicio.
        v.setTerminadaEn(cuando.isBefore(v.getIniciadaEn()) ? v.getIniciadaEn() : cuando);
        v.setTerminadaPor(u);
        v.setUpdatedAt(OffsetDateTime.now());
        visitas.saveAndFlush(v);
    }

    /** Personas a las que se les puede asignar una visita. */
    public List<Usuario> asignables() {
        return usuarios.findByActivo(true).stream()
                .filter(u -> ASIGNABLES.contains(u.getRol()))
                .sorted(Comparator.comparing(Usuario::getNombre))
                .toList();
    }

    /**
     * Visitas agendadas de la persona que se cruzan con [inicio, inicio + duración). Las que solo se tocan en
     * el borde (una termina a las 10:00 y la otra empieza a las 10:00) no se cruzan.
     */
    public List<Visita> cruces(UUID personaId, OffsetDateTime inicio, int duracionMinutos, UUID excepto) {
        OffsetDateTime fin = inicio.plusMinutes(duracionMinutos);
        return visitas.agendadasDeEntre(personaId, inicio.minusMinutes(DURACION_MAXIMA), fin).stream()
                .filter(o -> !o.getId().equals(excepto))
                .filter(o -> o.getFechaHora().isBefore(fin)
                        && o.getFechaHora().plusMinutes(o.getDuracionMinutos()).isAfter(inicio))
                .toList();
    }

    private void avisarCruces(Usuario persona, OffsetDateTime inicio, int duracionMinutos, UUID excepto) {
        List<Visita> choques = cruces(persona.getId(), inicio, duracionMinutos, excepto);
        if (choques.isEmpty()) {
            return;
        }
        List<CruceAgendaException.Cruce> detalle = choques.stream().map(o -> new CruceAgendaException.Cruce(
                o.getId(), o.getProyecto().getId(), o.getNumeroVisita(),
                o.getProyecto().getCotizacion().getCliente().getNombre(), persona.getNombre(), o.getFechaHora(),
                o.getFechaHora().plusMinutes(o.getDuracionMinutos()))).toList();
        CruceAgendaException.Cruce primero = detalle.get(0);
        String cuando = primero.inicio().atZoneSameInstant(ZONA).format(FECHA_HORA).toLowerCase(Locale.ROOT);
        throw new CruceAgendaException(persona.getNombre() + " ya tiene una visita a esa hora: "
                + primero.cliente() + ", " + cuando
                + (detalle.size() > 1 ? " (y " + (detalle.size() - 1) + " más)" : ""), detalle);
    }

    public Visita buscar(UUID id) {
        return visitas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Visita no encontrada: " + id));
    }

    // ---------- apoyo ----------

    /** Nadie marcó el fin: termina con la última tarea que se completó en ella (o al iniciarse). */
    private void terminarSola(Proyecto p, Visita v) {
        OffsetDateTime fin = tareas.findByCompletadaEnVisitaId(v.getId()).stream()
                .map(ChecklistTarea::getCompletadaEn)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(v.getIniciadaEn());
        v.setEstado(EstadoVisita.COMPLETADA);
        v.setTerminadaEn(fin);
        v.setUpdatedAt(OffsetDateTime.now());
        visitas.saveAndFlush(v);
        eventos.save(new ProyectoEvento(p, "VISITA_TERMINADA", null,
                "Visita " + v.getNumeroVisita() + " (se cerró sola: no se marcó el fin)"));
    }

    private void cambiarEstado(Proyecto p, EstadoProyecto nuevo, Usuario u, String antes) {
        p.setEstado(nuevo);
        p.setUpdatedAt(OffsetDateTime.now());
        eventos.save(new ProyectoEvento(p, "ESTADO", u, antes + " → " + etiqueta(nuevo)));
    }

    private Usuario asignable(UUID id) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
        if (!Boolean.TRUE.equals(u.getActivo()) || !ASIGNABLES.contains(u.getRol())) {
            throw new BadRequestException(u.getNombre() + " no puede recibir visitas");
        }
        return u;
    }

    private static void validarFecha(OffsetDateTime fechaHora) {
        if (dia(fechaHora).isBefore(LocalDate.now(ZONA))) {
            throw new BadRequestException("La fecha de la visita ya pasó");
        }
    }

    private static void verificarVersion(Visita v, Long version) {
        if (!v.getVersion().equals(version)) {
            throw new ConflictException("Alguien más modificó esta visita. Recarga y vuelve a intentar.");
        }
    }

    private static String descripcionAgenda(Visita v) {
        return "Visita " + v.getNumeroVisita() + ": " + v.getAsignadoA().getNombre() + ", "
                + v.getFechaHora().atZoneSameInstant(ZONA).format(FECHA_HORA).toLowerCase(Locale.ROOT);
    }

    static LocalDate dia(OffsetDateTime t) {
        return t.atZoneSameInstant(ZONA).toLocalDate();
    }

    static String etiqueta(EstadoProyecto e) {
        return switch (e) {
            case COTIZADO -> "Cotizado";
            case EN_INSTALACION -> "En instalación";
            case REQUIERE_RETORNO -> "Requiere retorno";
            case CERRADO -> "Cerrado";
        };
    }
}
