package com.appdomotica.proyectos.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.entity.InformeTecnico;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import com.appdomotica.cotizaciones.service.InformeTecnicoService;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.ValidadorImagen;
import com.appdomotica.proyectos.dto.ProyectoDtos;
import com.appdomotica.proyectos.dto.ProyectoDtos.*;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.entity.Visita;
import com.appdomotica.proyectos.repository.*;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Proyecto de instalación: instrucciones vigentes, ubicaciones del informe y checklist con evidencia.
 * Completar una tarea exige foto de antes y de después (RF-PRY-10); "no aplica" exige una nota.
 * Una ubicación con checklist propia se completa marcando todos sus puntos, con foto de antes y de después
 * de la ubicación (ver {@link ReglaUbicacion}).
 */
@Service
@RequiredArgsConstructor
public class ProyectoService {
    public static final String ENTIDAD_TAREA = "CHECKLIST_TAREA";
    public static final String FOTO_ANTES = "EVIDENCIA_ANTES";
    public static final String FOTO_DESPUES = "EVIDENCIA_DESPUES";
    public static final String ENTIDAD_CAMBIO = "CAMBIO_INSTRUCCION";
    public static final String FOTO_CAMBIO = "FOTO_CAMBIO";
    /** Fotos de antes y después de una ubicación con checklist (entidad_id = id de la ubicación). */
    public static final String ENTIDAD_UBICACION = "PROYECTO_UBICACION";
    private static final int MAX_FOTOS_POR_MOMENTO = 5;

    private final ProyectoRepository proyectos;
    private final InstruccionVersionRepository versiones;
    private final InstruccionItemRepository items;
    private final ChecklistTareaRepository tareas;
    private final ProyectoEventoRepository eventos;
    private final ArchivoAdjuntoRepository archivosRepo;
    private final ArchivoAdjuntoService archivos;
    private final InformeTecnicoService informes;
    private final VisitaService visitas;
    private final VisitaRepository visitasRepo;
    private final FirmaConformidadRepository firmas;
    private final ReporteEntregaRepository reportes;
    private final JdbcTemplate jdbc;
    private final CambioInstruccionRepository cambios;
    private final ProyectoChecklistItemRepository checklist;

    @Transactional(readOnly = true)
    public List<ProyectoResumen> listar() {
        List<Proyecto> lista = proyectos.findAllByOrderByFechaCreacionDesc();
        Map<UUID, long[]> avance = new HashMap<>();
        if (!lista.isEmpty()) {
            List<UUID> ids = lista.stream().map(Proyecto::getId).toList();
            for (Object[] fila : tareas.avance(ids)) {
                avance.put((UUID) fila[0], new long[]{(Long) fila[1], (Long) fila[2]});
            }
            // La barra cuenta tareas terminadas más puntos de checklist marcados.
            for (Object[] fila : checklist.avance(ids)) {
                avance.merge((UUID) fila[0], new long[]{(Long) fila[1], (Long) fila[2]},
                        (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
            }
        }
        Map<UUID, List<Visita>> porVenir = lista.isEmpty() ? Map.of()
                : visitasRepo.porVenir(lista.stream().map(Proyecto::getId).toList()).stream()
                .collect(Collectors.groupingBy(v -> v.getProyecto().getId()));
        return lista.stream().map(p -> {
            var c = p.getCotizacion();
            long[] a = avance.getOrDefault(p.getId(), new long[]{0, 0});
            List<Visita> vs = porVenir.getOrDefault(p.getId(), List.of());
            boolean retorno = vs.stream().anyMatch(v -> v.getEstado() == EstadoVisita.PENDIENTE_AGENDAR);
            OffsetDateTime proxima = vs.stream().map(Visita::getFechaHora).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            return new ProyectoResumen(p.getId(), p.getEstado(), c.getCliente().getNombre(),
                    c.getCliente().getTelefono(), c.getCliente().getDireccion(), c.getNumero(),
                    p.getFechaCreacion(), (int) a[0], (int) a[1], retorno, proxima);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProyectoDetalle obtener(UUID id) {
        return detalle(buscar(id));
    }

    /** Agrega una foto de antes o de después a una tarea pendiente. */
    @Transactional
    public ProyectoDetalle subirEvidencia(UUID tareaId, String momento, MultipartFile foto, Usuario usuario) {
        String categoria = categoriaDe(momento);
        ChecklistTarea t = buscarTareaModificable(tareaId);
        if (t.getEstado() != EstadoTarea.PENDIENTE) {
            throw new ConflictException("La tarea ya está cerrada; reábrela para cambiar sus fotos");
        }
        ValidadorImagen.validar(foto);
        if (fotos(t.getId(), categoria).size() >= MAX_FOTOS_POR_MOMENTO) {
            throw new BadRequestException("Como mucho " + MAX_FOTOS_POR_MOMENTO + " fotos de "
                    + momento.toLowerCase(Locale.ROOT) + " por tarea");
        }
        archivos.subirArchivo(foto, ENTIDAD_TAREA, t.getId(), categoria, t.getDescripcion(), usuario);
        return detalle(t.getInstruccionVersion().getProyecto());
    }

    @Transactional
    public ProyectoDetalle eliminarEvidencia(UUID tareaId, UUID fotoId) {
        ChecklistTarea t = buscarTareaModificable(tareaId);
        if (t.getEstado() != EstadoTarea.PENDIENTE) {
            throw new ConflictException("La tarea ya está cerrada; reábrela para cambiar sus fotos");
        }
        boolean propia = archivosRepo.findByEntidadTipoAndEntidadIdAndDeletedAtIsNull(ENTIDAD_TAREA, t.getId())
                .stream().anyMatch(a -> a.getId().equals(fotoId));
        if (!propia) {
            throw new ResourceNotFoundException("La foto no pertenece a esta tarea");
        }
        archivos.eliminarArchivo(fotoId);
        return detalle(t.getInstruccionVersion().getProyecto());
    }

    /** Completar, marcar "no aplica" o reabrir una tarea. */
    @Transactional
    public ProyectoDetalle cambiarEstadoTarea(UUID tareaId, Long version, EstadoTarea nuevo, String notas,
                                              OffsetDateTime ocurridoEn, Usuario usuario) {
        ChecklistTarea t = buscarTareaModificable(tareaId);
        if (!t.getVersion().equals(version)) {
            throw new ConflictException("Alguien más modificó esta tarea. Recarga y vuelve a intentar.");
        }
        String nota = notas == null || notas.isBlank() ? null : notas.trim();
        switch (nuevo) {
            case COMPLETADA -> {
                if (fotos(t.getId(), FOTO_ANTES).isEmpty() || fotos(t.getId(), FOTO_DESPUES).isEmpty()) {
                    throw new ConflictException("Para completar la tarea faltan fotos: se necesita al menos una "
                            + "de antes y una de después");
                }
            }
            case NO_APLICA -> {
                if (nota == null) {
                    throw new BadRequestException("Explica en la nota por qué la tarea no aplica");
                }
            }
            case PENDIENTE -> {
                // Reabrir: se conserva la evidencia y la nota anterior si no se manda otra.
                if (nota == null) {
                    nota = t.getNotas();
                }
            }
        }
        Proyecto p = abierto(t.getInstruccionVersion().getProyecto().getId());
        OffsetDateTime cuando = VisitaService.momento(ocurridoEn);
        // El avance queda en la visita en curso; si no hay, se inicia (RF-PRY-11).
        Visita visita = nuevo == EstadoTarea.PENDIENTE ? null : visitas.iniciar(p, usuario, cuando);
        t.setEstado(nuevo);
        t.setNotas(nota);
        t.setCompletadaPor(visita == null ? null : usuario);
        t.setCompletadaEn(visita == null ? null : cuando);
        t.setCompletadaEnVisitaId(visita == null ? null : visita.getId());
        t.setUpdatedAt(OffsetDateTime.now());
        tareas.saveAndFlush(t);

        String tipo = switch (nuevo) {
            case COMPLETADA -> "TAREA_COMPLETADA";
            case NO_APLICA -> "TAREA_NO_APLICA";
            case PENDIENTE -> "TAREA_REABIERTA";
        };
        eventos.save(new ProyectoEvento(p, tipo, usuario,
                t.getDescripcion() + (nuevo == EstadoTarea.NO_APLICA ? " — " + nota : "")));
        return detalle(p);
    }

    // ---------- checklist por ubicación ----------

    /**
     * Marca o desmarca un punto de la checklist de una ubicación. Es idempotente (mandar dos veces lo mismo no
     * cambia nada) y gana el último cambio. Marcar inicia la visita si no hay una en curso (RF-PRY-11).
     */
    @Transactional
    public ProyectoDetalle marcarItem(UUID itemId, boolean marcado, OffsetDateTime ocurridoEn, Usuario usuario) {
        ProyectoChecklistItem item = checklist.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Punto de la checklist no encontrado: " + itemId));
        Proyecto p = abierto(item.getProyecto().getId());
        if (item.isMarcado() == marcado) {
            return detalle(p);
        }
        OffsetDateTime cuando = VisitaService.momento(ocurridoEn);
        Visita visita = marcado ? visitas.iniciar(p, usuario, cuando) : null;
        item.setMarcado(marcado);
        item.setMarcadoPor(marcado ? usuario : null);
        item.setMarcadoEn(marcado ? cuando : null);
        item.setMarcadoEnVisitaId(visita != null ? visita.getId() : null);
        item.setUpdatedAt(OffsetDateTime.now());
        checklist.saveAndFlush(item);
        eventos.save(new ProyectoEvento(p, marcado ? "PUNTO_MARCADO" : "PUNTO_DESMARCADO", usuario,
                "Ubicación " + numerosDeUbicacion(p).getOrDefault(item.getUbicacion().getId(), 0) + ": " + item.getTexto()));
        return detalle(p);
    }

    /** Foto de antes o de después de una ubicación con checklist. */
    @Transactional
    public ProyectoDetalle subirFotoUbicacion(UUID proyectoId, UUID ubicacionId, String momento, MultipartFile foto,
                                              Usuario usuario) {
        String categoria = categoriaDe(momento);
        Proyecto p = abierto(proyectoId);
        Integer numero = ubicacionConChecklist(p, ubicacionId);
        ValidadorImagen.validar(foto);
        if (fotosUbicacion(ubicacionId, categoria).size() >= MAX_FOTOS_POR_MOMENTO) {
            throw new BadRequestException("Como mucho " + MAX_FOTOS_POR_MOMENTO + " fotos de "
                    + momento.toLowerCase(Locale.ROOT) + " por ubicación");
        }
        archivos.subirArchivo(foto, ENTIDAD_UBICACION, ubicacionId, categoria, "Ubicación " + numero, usuario);
        return detalle(p);
    }

    @Transactional
    public ProyectoDetalle eliminarFotoUbicacion(UUID proyectoId, UUID ubicacionId, UUID fotoId) {
        Proyecto p = abierto(proyectoId);
        ubicacionConChecklist(p, ubicacionId);
        boolean propia = archivosRepo.findByEntidadTipoAndEntidadIdAndDeletedAtIsNull(ENTIDAD_UBICACION, ubicacionId)
                .stream().anyMatch(a -> a.getId().equals(fotoId));
        if (!propia) {
            throw new ResourceNotFoundException("La foto no pertenece a esta ubicación");
        }
        archivos.eliminarArchivo(fotoId);
        return detalle(p);
    }

    /** Número de la ubicación si es del proyecto y tiene checklist. */
    private Integer ubicacionConChecklist(Proyecto p, UUID ubicacionId) {
        Integer numero = numerosDeUbicacion(p).get(ubicacionId);
        if (numero == null) {
            throw new ResourceNotFoundException("La ubicación no es de este proyecto");
        }
        boolean tiene = checklist.findByProyectoIdOrderByOrdenAsc(p.getId()).stream()
                .anyMatch(i -> i.getUbicacion().getId().equals(ubicacionId));
        if (!tiene) {
            throw new ConflictException("Esta ubicación no tiene checklist: las fotos van en cada tarea");
        }
        return numero;
    }

    /** Lo que les falta a las ubicaciones con checklist para cerrar el proyecto (RN-07). */
    List<String> faltaEnUbicaciones(Proyecto p) {
        return detalle(p).ubicaciones().stream()
                .filter(u -> !u.checklist().isEmpty())
                .flatMap(u -> ReglaUbicacion.falta(u.numero(), u.checklist().size(),
                        (int) u.checklist().stream().filter(ItemChecklist::marcado).count(),
                        u.fotosAntes().size(), u.fotosDespues().size()).stream())
                .toList();
    }

    private List<ArchivoAdjunto> fotosUbicacion(UUID ubicacionId, String categoria) {
        return archivosRepo.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(ENTIDAD_UBICACION, ubicacionId,
                categoria);
    }

    // ---------- visitas ----------

    /** El equipo llegó al sitio. Si ya hay una visita en curso de hoy, no hace nada. */
    @Transactional
    public ProyectoDetalle iniciarVisita(UUID proyectoId, OffsetDateTime ocurridoEn, Usuario usuario) {
        Proyecto p = abierto(proyectoId);
        visitas.iniciar(p, usuario, VisitaService.momento(ocurridoEn));
        return detalle(p);
    }

    /** Termina la visita en curso (si no hay, no hace nada). */
    @Transactional
    public ProyectoDetalle terminarVisita(UUID proyectoId, OffsetDateTime ocurridoEn, Usuario usuario) {
        Proyecto p = abierto(proyectoId);
        visitas.terminar(p, usuario, VisitaService.momento(ocurridoEn));
        return detalle(p);
    }

    @Transactional
    public ProyectoDetalle requiereRetorno(UUID proyectoId, String motivo, OffsetDateTime ocurridoEn, Usuario usuario) {
        Proyecto p = abierto(proyectoId);
        visitas.requiereRetorno(p, usuario, motivo, VisitaService.momento(ocurridoEn));
        return detalle(p);
    }

    @Transactional
    public ProyectoDetalle agendarVisita(UUID proyectoId, AgendarRequest req, Usuario usuario) {
        Proyecto p = abierto(proyectoId);
        visitas.agendar(p, usuario, req.asignadoAId(), req.fechaHora(), req.motivo(),
                req.duracionMinutos() != null ? req.duracionMinutos() : VisitaService.DURACION, req.confirmarCruce());
        return detalle(p);
    }

    @Transactional
    public ProyectoDetalle reagendarVisita(UUID visitaId, ReagendarRequest req, Usuario usuario) {
        Proyecto p = abierto(visitas.buscar(visitaId).getProyecto().getId());
        visitas.reagendar(visitas.buscar(visitaId), req.version(), usuario, req.asignadoAId(), req.fechaHora(),
                req.duracionMinutos(), req.confirmarCruce());
        return detalle(p);
    }

    @Transactional
    public ProyectoDetalle cancelarVisita(UUID visitaId, Long version, Usuario usuario) {
        Proyecto p = abierto(visitas.buscar(visitaId).getProyecto().getId());
        visitas.cancelar(visitas.buscar(visitaId), version, usuario);
        return detalle(p);
    }

    @Transactional(readOnly = true)
    public List<Persona> personal() {
        return visitas.asignables().stream().map(u -> new Persona(u.getId(), u.getNombre(), u.getRol())).toList();
    }

    /** Lo que impide cerrar además de las tareas: cambios de instrucción y extras sin confirmar. */
    List<String> bloqueosCierre(Proyecto p) {
        List<String> bloqueos = new ArrayList<>();
        Integer cambios = jdbc.queryForObject("select count(*) from cambios_instruccion where proyecto_id = ? "
                + "and estado = 'PENDIENTE_CONFIRMACION'", Integer.class, p.getId());
        if (cambios != null && cambios > 0) {
            bloqueos.add(cambios == 1 ? "Hay un cambio de instrucciones sin confirmar"
                    : "Hay " + cambios + " cambios de instrucciones sin confirmar");
        }
        Integer extras = jdbc.queryForObject("select count(*) from cotizacion_extras where cotizacion_id = ? "
                + "and estado = 'PENDIENTE'", Integer.class, p.getCotizacion().getId());
        if (extras != null && extras > 0) {
            bloqueos.add(extras == 1 ? "Hay un extra de materiales sin confirmar"
                    : "Hay " + extras + " extras de materiales sin confirmar");
        }
        return bloqueos;
    }

    /** Proyecto bloqueado para modificarlo; rechaza si ya está cerrado. */
    Proyecto abierto(UUID id) {
        Proyecto p = bloquear(id);
        if (p.getEstado() == EstadoProyecto.CERRADO) {
            throw new ConflictException("El proyecto ya está cerrado");
        }
        return p;
    }

    Proyecto bloquear(UUID id) {
        return proyectos.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado: " + id));
    }

    // ---------- armado de la respuesta ----------

    ProyectoDetalle detalle(Proyecto p) {
        var c = p.getCotizacion();
        InformeTecnico informe = c.getInforme();

        List<InformeTecnicoUbicacion> ubis = informe != null ? informe.getUbicaciones() : List.of();
        Map<UUID, UUID> fotosUbi = informes.fotosPorUbicacion(ubis.stream().map(InformeTecnicoUbicacion::getId).toList());
        Map<UUID, Integer> numeroUbi = new HashMap<>();
        for (int i = 0; i < ubis.size(); i++) {
            numeroUbi.put(ubis.get(i).getId(), i + 1);
        }

        List<InstruccionVersion> vs = versiones.findByProyectoIdOrderByNumeroVersionDesc(p.getId());
        VersionInstrucciones vigente = vs.isEmpty() ? null : version(vs.get(0));

        List<Visita> vis = visitasRepo.findByProyectoIdOrderByNumeroVisitaAsc(p.getId());
        Map<UUID, Integer> numeroVisita = vis.stream().collect(Collectors.toMap(Visita::getId, Visita::getNumeroVisita));
        Map<UUID, Integer> hechasPorVisita = new HashMap<>();
        if (!vis.isEmpty()) {
            for (Object[] fila : tareas.avancePorVisita(numeroVisita.keySet())) {
                hechasPorVisita.put((UUID) fila[0], ((Long) fila[1]).intValue());
            }
            for (Object[] fila : checklist.avancePorVisita(numeroVisita.keySet())) {
                hechasPorVisita.merge((UUID) fila[0], ((Long) fila[1]).intValue(), Integer::sum);
            }
        }
        List<ProyectoDtos.Visita> visitasDto = vis.stream().map(v -> new ProyectoDtos.Visita(v.getId(),
                v.getNumeroVisita(), v.getEstado(), v.getAsignadoA() != null ? v.getAsignadoA().getId() : null,
                v.getAsignadoA() != null ? v.getAsignadoA().getNombre() : null, v.getFechaHora(),
                v.getDuracionMinutos(), v.getMotivo(),
                v.getMotivoRetorno(), v.getIniciadaEn(),
                v.getIniciadaPor() != null ? v.getIniciadaPor().getNombre() : null, v.getTerminadaEn(),
                hechasPorVisita.getOrDefault(v.getId(), 0), v.getVersion())).toList();
        Firma firma = firmas.findFirstByVisitaProyectoId(p.getId()).map(f -> new Firma(f.getFirmadoPorCliente(),
                f.getFecha(), f.getArchivoId(), f.getVisita().getNumeroVisita(),
                f.getRegistradaPor() != null ? f.getRegistradaPor().getNombre() : null)).orElse(null);

        List<ChecklistTarea> lista = tareas.vigentesDelProyecto(p.getId());
        Map<UUID, Map<String, List<UUID>>> evidencia = evidencia(lista.stream().map(ChecklistTarea::getId).toList());
        List<Tarea> tareasDto = lista.stream().map(t -> {
            var ev = evidencia.getOrDefault(t.getId(), Map.of());
            UUID ubi = t.getUbicacion() != null ? t.getUbicacion().getId() : null;
            return new Tarea(t.getId(), t.getDescripcion(), t.getEstado(), t.getNotas(), t.getCantidad(),
                    t.getProducto() != null ? t.getProducto().getNombre() : null, ubi,
                    ubi != null ? numeroUbi.get(ubi) : null, t.getInstruccionVersion().getNumeroVersion(),
                    ev.getOrDefault(FOTO_ANTES, List.of()), ev.getOrDefault(FOTO_DESPUES, List.of()),
                    t.getCompletadaPor() != null ? t.getCompletadaPor().getNombre() : null,
                    t.getCompletadaEn(),
                    t.getCompletadaEnVisitaId() != null ? numeroVisita.get(t.getCompletadaEnVisitaId()) : null,
                    t.getVersion());
        }).toList();

        // Ubicaciones: con checklist, sus puntos, sus fotos y si quedó completada (siempre calculado).
        Map<UUID, List<ProyectoChecklistItem>> puntos = checklist.findByProyectoIdOrderByOrdenAsc(p.getId()).stream()
                .collect(Collectors.groupingBy(i -> i.getUbicacion().getId()));
        Map<UUID, Map<String, List<UUID>>> fotosDeUbicacion = evidenciaDe(ENTIDAD_UBICACION, List.copyOf(puntos.keySet()));
        List<UbicacionProyecto> ubicaciones = new ArrayList<>();
        for (InformeTecnicoUbicacion u : ubis) {
            List<ProyectoChecklistItem> deAqui = puntos.getOrDefault(u.getId(), List.of());
            var fotos = fotosDeUbicacion.getOrDefault(u.getId(), Map.of());
            List<UUID> antes = fotos.getOrDefault(FOTO_ANTES, List.of());
            List<UUID> despues = fotos.getOrDefault(FOTO_DESPUES, List.of());
            int pendientes = (int) tareasDto.stream()
                    .filter(t -> u.getId().equals(t.ubicacionId()) && t.estado() == EstadoTarea.PENDIENTE).count();
            int marcados = (int) deAqui.stream().filter(ProyectoChecklistItem::isMarcado).count();
            ubicaciones.add(new UbicacionProyecto(u.getId(), numeroUbi.get(u.getId()), u.getNota(), fotosUbi.get(u.getId()),
                    deAqui.stream().map(i -> new ItemChecklist(i.getId(), i.getTexto(), i.isMarcado(),
                            i.getMarcadoPor() != null ? i.getMarcadoPor().getNombre() : null, i.getMarcadoEn(),
                            i.getMarcadoEnVisitaId() != null ? numeroVisita.get(i.getMarcadoEnVisitaId()) : null)).toList(),
                    antes, despues,
                    ReglaUbicacion.completada(deAqui.size(), marcados, antes.size(), despues.size(), pendientes)));
        }

        List<Evento> bitacora = eventos.findByProyectoIdOrderByFechaDesc(p.getId()).stream()
                .map(e -> new Evento(e.getTipo(), e.getUsuarioNombre(), e.getDetalle(), e.getFecha()))
                .toList();

        return new ProyectoDetalle(p.getId(), p.getEstado(), c.getCliente().getNombre(), c.getCliente().getTelefono(),
                c.getCliente().getDireccion(), c.getNumero(), informe != null ? informe.getNumeroVersion() : null,
                informe != null ? informe.getNotas() : null, p.getFechaCreacion(), p.getVersion(), vigente,
                ubicaciones, tareasDto, bitacora, visitasDto, firma, reportes.findByProyectoId(p.getId()).isPresent(),
                p.getEstado() == EstadoProyecto.CERRADO ? List.of() : bloqueosCierre(p),
                cambiosDto(p, numeroUbi), historial(vs, p),
                c.getExtras().stream().map(e -> new ExtraProyecto(e.getNumero(), e.getEstado(), e.getMotivo(),
                        e.getItems().stream().map(i -> new ItemExtraProyecto(i.getProductoNombre(), i.getCantidad()))
                                .toList())).toList());
    }

    /** Contenido de una versión de las instrucciones. */
    VersionInstrucciones version(InstruccionVersion v) {
        return new VersionInstrucciones(v.getId(), v.getNumeroVersion(), v.getGeneradaDesde(),
                v.getCreadoPor() != null ? v.getCreadoPor().getNombre() : null, v.getFecha(),
                items.findByInstruccionVersionIdOrderByOrdenAsc(v.getId()).stream()
                        .map(i -> new ItemInstruccion(i.getId(),
                                i.getProducto() != null ? i.getProducto().getNombre() : null,
                                i.getCantidad(), i.getDescripcion(), i.getAccion()))
                        .toList());
    }

    /** Una versión anterior (o la vigente) por su número, para el historial (RF-PRY-07). */
    @Transactional(readOnly = true)
    public VersionInstrucciones versionPorNumero(UUID proyectoId, int numero) {
        buscar(proyectoId);
        return version(versiones.findByProyectoIdAndNumeroVersion(proyectoId, numero)
                .orElseThrow(() -> new ResourceNotFoundException("Esa versión de las instrucciones no existe")));
    }

    private List<VersionResumen> historial(List<InstruccionVersion> vs, Proyecto p) {
        Map<UUID, CambioInstruccion> porVersion = new HashMap<>();
        for (CambioInstruccion ci : cambios.findByProyectoId(p.getId())) {
            if (ci.getInstruccionNueva() != null) {
                porVersion.put(ci.getInstruccionNueva().getId(), ci);
            }
        }
        return vs.stream().map(v -> {
            CambioInstruccion ci = porVersion.get(v.getId());
            return new VersionResumen(v.getNumeroVersion(), v.getGeneradaDesde(),
                    v.getCreadoPor() != null ? v.getCreadoPor().getNombre() : null, v.getFecha(),
                    ci != null ? ci.getDescripcion() : null, ci != null ? ci.getPropuestoPor().getNombre() : null);
        }).toList();
    }

    /** Cambios del proyecto, los pendientes primero y luego del más nuevo al más viejo. */
    private List<Cambio> cambiosDto(Proyecto p, Map<UUID, Integer> numeroUbi) {
        List<CambioInstruccion> lista = new ArrayList<>(cambios.findByProyectoId(p.getId()));
        if (lista.isEmpty()) {
            return List.of();
        }
        lista.sort(Comparator.comparing((CambioInstruccion ci) -> ci.getEstado() != EstadoCambio.PENDIENTE_CONFIRMACION)
                .thenComparing(CambioInstruccion::getFechaPropuesta, Comparator.reverseOrder()));
        Map<UUID, List<UUID>> fotos = archivosRepo.findByEntidadTipoAndEntidadIdInAndCategoriaAndDeletedAtIsNull(
                        ENTIDAD_CAMBIO, lista.stream().map(CambioInstruccion::getId).toList(), FOTO_CAMBIO).stream()
                .sorted(Comparator.comparing(ArchivoAdjunto::getCreatedAt))
                .collect(Collectors.groupingBy(ArchivoAdjunto::getEntidadId,
                        Collectors.mapping(ArchivoAdjunto::getId, Collectors.toList())));
        return lista.stream().map(ci -> new Cambio(ci.getId(), ci.getEstado(), ci.getDescripcion(),
                Boolean.TRUE.equals(ci.getRequiereMaterial()), ci.getPropuestoPor().getNombre(),
                ci.getPropuestoPor().getId(), ci.getFechaPropuesta(),
                ci.getConfirmadoPor() != null ? ci.getConfirmadoPor().getNombre() : null, ci.getFechaConfirmacion(),
                ci.getMotivoRechazo(), ci.getInstruccionAnterior().getNumeroVersion(),
                ci.getInstruccionNueva() != null ? ci.getInstruccionNueva().getNumeroVersion() : null,
                ci.getLineas().stream().map(l -> linea(l, numeroUbi)).toList(),
                fotos.getOrDefault(ci.getId(), List.of()),
                ci.getExtra() != null ? ci.getExtra().getNumero() : null,
                ci.getExtra() != null ? ci.getExtra().getEstado() : null, ci.getVersion())).toList();
    }

    static LineaCambio linea(CambioInstruccionLinea l, Map<UUID, Integer> numeroUbi) {
        Integer ubi = l.getUbicacion() != null ? numeroUbi.get(l.getUbicacion().getId()) : null;
        String donde = ubi != null ? " (ubicación " + ubi + ")" : "";
        ChecklistTarea t = l.getTarea();
        String producto = l.getProducto() != null ? l.getProducto().getNombre() : null;
        String texto = switch (l.getTipo()) {
            case AGREGAR -> producto != null
                    ? "Agregar " + l.getCantidad() + " × " + producto + donde
                    + (l.getDescripcion() != null ? " — " + l.getDescripcion() : "")
                    : "Agregar tarea: " + l.getDescripcion() + donde;
            case CAMBIAR_CANTIDAD -> "Cambiar cantidad de " + t.getCantidad() + " a " + l.getCantidad() + ": "
                    + t.getDescripcion();
            case QUITAR -> "Quitar: " + t.getDescripcion();
        };
        return new LineaCambio(l.getTipo(), texto, t != null ? t.getId() : null, producto, ubi, l.getCantidad(),
                t != null ? t.getCantidad() : null, l.getDescripcion());
    }

    /** Fotos de antes y después de cada tarea, de la más antigua a la más nueva. */
    private Map<UUID, Map<String, List<UUID>>> evidencia(List<UUID> tareaIds) {
        return evidenciaDe(ENTIDAD_TAREA, tareaIds);
    }

    private Map<UUID, Map<String, List<UUID>>> evidenciaDe(String entidad, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ArchivoAdjunto> todas = new ArrayList<>();
        for (String cat : List.of(FOTO_ANTES, FOTO_DESPUES)) {
            todas.addAll(archivosRepo.findByEntidadTipoAndEntidadIdInAndCategoriaAndDeletedAtIsNull(entidad, ids, cat));
        }
        return todas.stream()
                .sorted(Comparator.comparing(ArchivoAdjunto::getCreatedAt))
                .collect(Collectors.groupingBy(ArchivoAdjunto::getEntidadId,
                        Collectors.groupingBy(ArchivoAdjunto::getCategoria,
                                Collectors.mapping(ArchivoAdjunto::getId, Collectors.toList()))));
    }

    private List<ArchivoAdjunto> fotos(UUID tareaId, String categoria) {
        return archivosRepo.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(ENTIDAD_TAREA, tareaId, categoria);
    }

    private static String categoriaDe(String momento) {
        return switch (momento == null ? "" : momento.toUpperCase(Locale.ROOT)) {
            case "ANTES" -> FOTO_ANTES;
            case "DESPUES", "DESPUÉS" -> FOTO_DESPUES;
            default -> throw new BadRequestException("El momento de la foto debe ser ANTES o DESPUES");
        };
    }

    private ChecklistTarea buscarTareaModificable(UUID id) {
        ChecklistTarea t = tareas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + id));
        if (!Boolean.TRUE.equals(t.getVigente())) {
            throw new ConflictException("Esta tarea fue reemplazada por una versión nueva de las instrucciones");
        }
        if (t.getInstruccionVersion().getProyecto().getEstado() == EstadoProyecto.CERRADO) {
            throw new ConflictException("El proyecto ya está cerrado");
        }
        return t;
    }

    Map<UUID, Integer> numerosDeUbicacion(Proyecto p) {
        var informe = p.getCotizacion().getInforme();
        Map<UUID, Integer> numeros = new HashMap<>();
        if (informe != null) {
            for (int i = 0; i < informe.getUbicaciones().size(); i++) {
                numeros.put(informe.getUbicaciones().get(i).getId(), i + 1);
            }
        }
        return numeros;
    }

    Proyecto buscarProyecto(UUID id) {
        return buscar(id);
    }

    private Proyecto buscar(UUID id) {
        return proyectos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado: " + id));
    }
}
