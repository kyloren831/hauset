package com.appdomotica.tareas.service;

import com.appdomotica.clientes.repository.LeadRepository;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.repository.CotizacionRepository;
import com.appdomotica.proyectos.repository.ProyectoRepository;
import com.appdomotica.tareas.dto.TareaEquipoDtos.*;
import com.appdomotica.tareas.entity.EstadoTareaEquipo;
import com.appdomotica.tareas.entity.OrigenTareaEquipo;
import com.appdomotica.tareas.entity.TareaEquipo;
import com.appdomotica.tareas.repository.TareaEquipoRepository;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lista de tareas de la oficina (SRS 3.8, RF-TAR-01..08). */
@Service
@RequiredArgsConstructor
public class TareaEquipoService {
    /** Quienes usan la lista (RF-TAR-07). */
    public static final Set<RolUsuario> ROLES = Set.of(
            RolUsuario.JEFE_SISTEMAS, RolUsuario.JEFE_ELECTROMECANICO, RolUsuario.SECRETARIA);

    /** Las hechas se muestran este tiempo; luego salen del tablero. */
    static final int DIAS_HECHAS_VISIBLES = 30;

    private final TareaEquipoRepository tareas;
    private final UsuarioRepository usuarios;
    private final LeadRepository leads;
    private final CotizacionRepository cotizaciones;
    private final ProyectoRepository proyectos;

    @Transactional(readOnly = true)
    public List<TareaResponse> listar(Usuario yo) {
        return tareas.visiblesPara(yo.getId(), OffsetDateTime.now().minusDays(DIAS_HECHAS_VISIBLES)).stream()
                .map(TareaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonaResponse> personas() {
        return usuarios.findByActivo(true).stream()
                .filter(u -> ROLES.contains(u.getRol()))
                .sorted(Comparator.comparing(Usuario::getNombre))
                .map(PersonaResponse::de).toList();
    }

    @Transactional
    public TareaResponse crear(NuevaTareaRequest req, Usuario yo) {
        long ligados = java.util.stream.Stream.of(req.leadId(), req.cotizacionId(), req.proyectoId())
                .filter(java.util.Objects::nonNull).count();
        if (ligados > 1) {
            throw new BadRequestException("Una tarea se liga a un solo registro: lead, cotización o proyecto");
        }
        TareaEquipo t = new TareaEquipo();
        t.setCreadaPor(yo);
        aplicar(t, req.titulo(), req.descripcion(), req.fechaLimite(), req.asignadaAId(), req.privada(), yo);
        if (req.leadId() != null) {
            t.setLead(leads.findById(req.leadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado: " + req.leadId())));
        }
        if (req.cotizacionId() != null) {
            t.setCotizacion(cotizaciones.findById(req.cotizacionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada: " + req.cotizacionId())));
        }
        if (req.proyectoId() != null) {
            t.setProyecto(proyectos.findById(req.proyectoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado: " + req.proyectoId())));
        }
        return TareaResponse.de(tareas.saveAndFlush(t));
    }

    @Transactional
    public TareaResponse editar(UUID id, EditarTareaRequest req, Usuario yo) {
        TareaEquipo t = buscarVisible(id, req.version(), yo);
        if (req.privada() != t.isPrivada() && !esCreador(t, yo)) {
            throw new AccessDeniedException("Solo quien creó la tarea la vuelve privada o pública");
        }
        if (req.privada() && t.getOrigen() != OrigenTareaEquipo.MANUAL) {
            throw new BadRequestException("Una tarea automática es de todos y no puede ser privada");
        }
        aplicar(t, req.titulo(), req.descripcion(), req.fechaLimite(), req.asignadaAId(), req.privada(), yo);
        return guardar(t);
    }

    /** Pasa la tarea a otra columna del tablero. Al terminarla queda quién y cuándo (RF-TAR-03). */
    @Transactional
    public TareaResponse mover(UUID id, MoverTareaRequest req, Usuario yo) {
        TareaEquipo t = buscarVisible(id, req.version(), yo);
        if (t.getEstado() == req.estado()) {
            return TareaResponse.de(t);
        }
        cambiarEstado(t, req.estado(), yo);
        return guardar(t);
    }

    /** La borra quien la creó o un jefe. */
    @Transactional
    public void eliminar(UUID id, Long version, Usuario yo) {
        TareaEquipo t = buscarVisible(id, version, yo);
        boolean esJefe = yo.getRol() == RolUsuario.JEFE_SISTEMAS || yo.getRol() == RolUsuario.JEFE_ELECTROMECANICO;
        if (!esCreador(t, yo) && !esJefe) {
            throw new AccessDeniedException("Solo quien creó la tarea o un jefe la elimina");
        }
        tareas.delete(t);
    }

    // ---------- tareas automáticas (RF-TAR-05) ----------

    /** El cliente aceptó: la oficina decide si se inicia el proyecto (RF-COT-18). */
    @Transactional
    public void alAceptarCotizacion(Cotizacion c, Usuario quien) {
        TareaEquipo t = new TareaEquipo();
        t.setOrigen(OrigenTareaEquipo.COTIZACION_ACEPTADA);
        t.setCreadaPor(quien);
        t.setCotizacion(c);
        t.setTitulo(recortar("Cotización aceptada: decidir si se inicia el proyecto de " + c.getCliente().getNombre()));
        t.setDescripcion("El cliente aceptó la cotización " + c.getNumero()
                + ". Revisen si se inicia el proyecto; para iniciarlo hace falta el informe técnico.");
        tareas.save(t);
    }

    /** Al iniciar el proyecto, la tarea automática de esa cotización queda hecha. */
    @Transactional
    public void alIniciarProyecto(Cotizacion c, Usuario quien) {
        for (TareaEquipo t : tareas.findByCotizacionIdAndOrigenAndEstadoNot(
                c.getId(), OrigenTareaEquipo.COTIZACION_ACEPTADA, EstadoTareaEquipo.HECHA)) {
            cambiarEstado(t, EstadoTareaEquipo.HECHA, quien);
            tareas.save(t);
        }
    }

    /** Un cambio de instrucciones propuesto en sitio espera a que un jefe lo confirme o rechace (RF-PRY-04). */
    @Transactional
    public void alProponerCambio(com.appdomotica.proyectos.entity.Proyecto p, UUID cambioId, String descripcion,
                                 Usuario quien) {
        TareaEquipo t = new TareaEquipo();
        t.setOrigen(OrigenTareaEquipo.CAMBIO_PROPUESTO);
        t.setOrigenRef(cambioId);
        t.setCreadaPor(quien);
        t.setProyecto(p);
        t.setTitulo(recortar("Revisar cambio de instrucciones en el proyecto de "
                + p.getCotizacion().getCliente().getNombre()));
        t.setDescripcion(quien.getNombre() + " propuso: " + descripcion
                + "\n\nUn jefe lo confirma o lo rechaza desde el proyecto, en la pestaña Instrucciones.");
        tareas.save(t);
    }

    /** Un extra de materiales espera precios (jefes o secretaria) y la confirmación de un jefe (RF-COT-10). */
    @Transactional
    public void alCrearExtra(Cotizacion c, UUID extraId, int numero, String motivo, Usuario quien) {
        TareaEquipo t = new TareaEquipo();
        t.setOrigen(OrigenTareaEquipo.EXTRA_PENDIENTE);
        t.setOrigenRef(extraId);
        t.setCreadaPor(quien);
        t.setCotizacion(c);
        t.setTitulo(recortar("Poner precios y confirmar el extra " + numero + " de " + c.getCliente().getNombre()));
        t.setDescripcion((motivo == null || motivo.isBlank() ? "" : "Motivo: " + motivo + "\n\n")
                + "Se arma en la cotización " + c.getNumero() + ", sección Extras de materiales. "
                + "Suma al total solo cuando un jefe lo confirma.");
        tareas.save(t);
    }

    /** El cambio o el extra ya se resolvió: su tarea automática queda hecha. */
    @Transactional
    public void alResolver(OrigenTareaEquipo origen, UUID ref, Usuario quien) {
        for (TareaEquipo t : tareas.findByOrigenAndOrigenRefAndEstadoNot(origen, ref, EstadoTareaEquipo.HECHA)) {
            cambiarEstado(t, EstadoTareaEquipo.HECHA, quien);
            tareas.save(t);
        }
    }

    // ---------- utilidades ----------

    private void aplicar(TareaEquipo t, String titulo, String descripcion, java.time.LocalDate fechaLimite,
                         UUID asignadaAId, boolean privada, Usuario yo) {
        String limpio = titulo == null ? "" : titulo.trim();
        if (limpio.isEmpty()) {
            throw new BadRequestException("La tarea necesita un título");
        }
        t.setTitulo(recortar(limpio));
        t.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
        t.setFechaLimite(fechaLimite);
        t.setPrivada(privada);
        Usuario asignada = null;
        if (asignadaAId != null) {
            asignada = usuarios.findById(asignadaAId)
                    .filter(u -> Boolean.TRUE.equals(u.getActivo()) && ROLES.contains(u.getRol()))
                    .orElseThrow(() -> new BadRequestException(
                            "Una tarea se asigna solo a uno de los jefes o a la secretaria"));
        }
        // Una privada es de quien la crea: no se le asigna a otra persona.
        if (privada && asignada != null && !asignada.getId().equals(t.getCreadaPor().getId())) {
            throw new BadRequestException("Una tarea privada no se asigna a otra persona");
        }
        t.setAsignadaA(asignada);
    }

    private static void cambiarEstado(TareaEquipo t, EstadoTareaEquipo estado, Usuario quien) {
        t.setEstado(estado);
        if (estado == EstadoTareaEquipo.HECHA) {
            t.setCompletadaPor(quien);
            t.setCompletadaEn(OffsetDateTime.now());
        } else {
            t.setCompletadaPor(null);
            t.setCompletadaEn(null);
        }
    }

    /** Las privadas de otra persona no existen para mí (404, no 403: no se revela que existen). */
    private TareaEquipo buscarVisible(UUID id, Long version, Usuario yo) {
        TareaEquipo t = tareas.findById(id)
                .filter(x -> !x.isPrivada() || esCreador(x, yo))
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + id));
        if (version != null && !version.equals(t.getVersion())) {
            throw new ConflictException("Alguien más cambió la tarea. Recarga y vuelve a intentar.");
        }
        return t;
    }

    private TareaResponse guardar(TareaEquipo t) {
        t.setUpdatedAt(OffsetDateTime.now());
        // Se responde con lo que devuelve el guardado: trae la versión nueva.
        return TareaResponse.de(tareas.saveAndFlush(t));
    }

    private static boolean esCreador(TareaEquipo t, Usuario yo) {
        return t.getCreadaPor().getId().equals(yo.getId());
    }

    private static String recortar(String s) {
        return s.length() <= 200 ? s : s.substring(0, 199) + "…";
    }
}
