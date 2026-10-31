package com.appdomotica.proyectos.service;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.entity.CotizacionExtra;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import com.appdomotica.cotizaciones.entity.OrigenExtra;
import com.appdomotica.cotizaciones.service.ExtraMaterialesService;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.ValidadorImagen;
import com.appdomotica.proyectos.dto.ProyectoDtos.*;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.repository.*;
import com.appdomotica.tareas.entity.OrigenTareaEquipo;
import com.appdomotica.tareas.service.TareaEquipoService;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Cambios de instrucciones en sitio (RF-PRY-03..08, RN-05, RN-06).
 * <ol>
 *   <li>El instalador o un jefe propone: descripción, líneas (agregar, cambiar cantidad, quitar), si
 *       requiere material y fotos opcionales. No toca el checklist todavía.</li>
 *   <li>Un jefe lo confirma (puede ser el mismo que lo propuso) o lo rechaza con un motivo.</li>
 *   <li>Al confirmar, en una transacción: versión N+1 de las instrucciones, tareas nuevas para lo agregado o
 *       cambiado, las reemplazadas dejan de estar vigentes (se conservan, RN-06) y, si requiere material,
 *       un extra pendiente en la cotización. Nunca una cotización nueva (RF-PRY-08).</li>
 * </ol>
 * Se aplica sobre la versión vigente al confirmar; si una tarea afectada ya no está pendiente, se rechaza
 * con 409 para que se proponga de nuevo.
 */
@Service
@RequiredArgsConstructor
public class CambioInstruccionService {
    private static final int MAX_FOTOS = 3;

    private final ProyectoService proyectos;
    private final CambioInstruccionRepository cambios;
    private final InstruccionVersionRepository versiones;
    private final InstruccionItemRepository items;
    private final ChecklistTareaRepository tareas;
    private final ProyectoEventoRepository eventos;
    private final ProductoCatalogoRepository productos;
    private final ArchivoAdjuntoService archivos;
    private final ArchivoAdjuntoRepository archivosRepo;
    private final ExtraMaterialesService extras;
    private final TareaEquipoService tareasEquipo;

    @Transactional
    public ProyectoDetalle proponer(UUID proyectoId, ProponerCambioRequest req, Usuario quien) {
        Proyecto p = proyectos.abierto(proyectoId);
        InstruccionVersion actual = vigente(p);
        String descripcion = req.descripcion().trim();

        CambioInstruccion c = new CambioInstruccion();
        c.setProyecto(p);
        c.setInstruccionAnterior(actual);
        c.setPropuestoPor(quien);
        c.setDescripcion(descripcion);
        c.setRequiereMaterial(req.requiereMaterial());
        // Versión nula = nuevo: así se inserta con sus líneas (con versión, Spring Data haría merge).
        c.setVersion(null);

        List<LineaCambioRequest> pedidas = req.lineas() == null ? List.of() : req.lineas();
        if (pedidas.isEmpty()) {
            // Sin líneas: el cambio deja una tarea con lo que se describió.
            pedidas = List.of(new LineaCambioRequest(TipoLineaCambio.AGREGAR, null, null, null, 1,
                    recortar(descripcion)));
        }
        Set<UUID> ubicacionesDelProyecto = proyectos.numerosDeUbicacion(p).keySet();
        Set<UUID> tareasVistas = new HashSet<>();
        int orden = 0;
        for (LineaCambioRequest r : pedidas) {
            CambioInstruccionLinea l = new CambioInstruccionLinea();
            l.setCambio(c);
            l.setOrden(orden++);
            l.setTipo(r.tipo());
            String nota = r.descripcion() == null || r.descripcion().isBlank() ? null : recortar(r.descripcion().trim());
            switch (r.tipo()) {
                case AGREGAR -> {
                    if (r.tareaId() != null) {
                        throw new BadRequestException("Para cambiar una tarea existente usa cambiar cantidad o quitar");
                    }
                    if (r.productoId() != null) {
                        l.setProducto(productos.findById(r.productoId())
                                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + r.productoId())));
                    } else if (nota == null) {
                        throw new BadRequestException("Una tarea nueva necesita un producto o una descripción");
                    }
                    if (r.ubicacionId() != null) {
                        if (!ubicacionesDelProyecto.contains(r.ubicacionId())) {
                            throw new BadRequestException("La ubicación no es del informe técnico de este proyecto");
                        }
                        l.setUbicacion(ubicacion(p, r.ubicacionId()));
                    }
                    l.setCantidad(r.cantidad() != null ? r.cantidad() : 1);
                    l.setDescripcion(nota);
                }
                case CAMBIAR_CANTIDAD, QUITAR -> {
                    ChecklistTarea t = tareaPendiente(p, r.tareaId());
                    if (!tareasVistas.add(t.getId())) {
                        throw new BadRequestException("La misma tarea aparece dos veces en el cambio");
                    }
                    if (r.tipo() == TipoLineaCambio.CAMBIAR_CANTIDAD) {
                        if (t.getCantidad() == null) {
                            throw new BadRequestException("Esa tarea no tiene cantidad; quítala y agrega otra");
                        }
                        if (r.cantidad() == null || r.cantidad().equals(t.getCantidad())) {
                            throw new BadRequestException("Indica la cantidad nueva de: " + t.getDescripcion());
                        }
                        l.setCantidad(r.cantidad());
                    }
                    l.setTarea(t);
                    l.setDescripcion(nota);
                }
            }
            c.getLineas().add(l);
        }
        c = cambios.save(c);
        eventos.save(new ProyectoEvento(p, "CAMBIO_PROPUESTO", quien, descripcion));
        tareasEquipo.alProponerCambio(p, c.getId(), descripcion, quien);
        return proyectos.detalle(p);
    }

    /** Foto opcional del cambio (RF-PRY-03), mientras espera confirmación. */
    @Transactional
    public ProyectoDetalle subirFoto(UUID cambioId, MultipartFile foto, Usuario quien) {
        Proyecto p = proyectos.abierto(proyectoDe(cambioId));
        CambioInstruccion c = buscar(cambioId);
        if (c.getEstado() != EstadoCambio.PENDIENTE_CONFIRMACION) {
            throw new ConflictException("El cambio ya fue resuelto");
        }
        ValidadorImagen.validar(foto);
        if (archivosRepo.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(
                ProyectoService.ENTIDAD_CAMBIO, c.getId(), ProyectoService.FOTO_CAMBIO).size() >= MAX_FOTOS) {
            throw new BadRequestException("Como mucho " + MAX_FOTOS + " fotos por cambio");
        }
        archivos.subirArchivo(foto, ProyectoService.ENTIDAD_CAMBIO, c.getId(), ProyectoService.FOTO_CAMBIO,
                "Cambio de instrucciones", quien);
        return proyectos.detalle(p);
    }

    /** Solo jefes (RF-PRY-04). */
    @Transactional
    public ProyectoDetalle confirmar(UUID cambioId, Long version, Usuario jefe) {
        Proyecto p = proyectos.abierto(proyectoDe(cambioId));
        CambioInstruccion c = buscar(cambioId);
        pendiente(c, version);
        InstruccionVersion anterior = vigente(p);

        for (CambioInstruccionLinea l : c.getLineas()) {
            if (l.getTarea() != null) {
                ChecklistTarea t = l.getTarea();
                if (!Boolean.TRUE.equals(t.getVigente()) || t.getEstado() != EstadoTarea.PENDIENTE) {
                    throw new ConflictException("La tarea \"" + t.getDescripcion() + "\" ya se completó o cambió "
                            + "desde que se propuso. Rechaza este cambio y propón uno nuevo.");
                }
            }
        }

        InstruccionVersion nueva = new InstruccionVersion();
        nueva.setProyecto(p);
        nueva.setNumeroVersion(anterior.getNumeroVersion() + 1);
        nueva.setGeneradaDesde("CAMBIO_SITIO");
        nueva.setCreadoPor(jefe);
        nueva = versiones.save(nueva);

        List<ChecklistTarea> vigentes = tareas.vigentesDelProyecto(p.getId());
        int orden = vigentes.stream().mapToInt(ChecklistTarea::getOrden).max().orElse(-1) + 1;
        // Material que hace falta: lo que se agrega y lo que sube de cantidad.
        Map<ProductoCatalogo, Integer> material = new LinkedHashMap<>();
        for (CambioInstruccionLinea l : c.getLineas()) {
            ChecklistTarea t = l.getTarea();
            switch (l.getTipo()) {
                case QUITAR -> {
                    t.setVigente(false);
                    t.setUpdatedAt(OffsetDateTime.now());
                    tareas.save(t);
                }
                case CAMBIAR_CANTIDAD -> {
                    t.setVigente(false);
                    t.setUpdatedAt(OffsetDateTime.now());
                    tareas.save(t);
                    ChecklistTarea reemplazo = nuevaTarea(nueva, t.getProducto(), l.getCantidad(), t.getUbicacion(),
                            null, orden++);
                    if (t.getProducto() == null) {
                        reemplazo.setDescripcion(t.getDescripcion());
                    }
                    reemplazo.setOrigenTareaId(t.getId());
                    tareas.save(reemplazo);
                    if (t.getProducto() != null && l.getCantidad() > t.getCantidad()) {
                        material.merge(t.getProducto(), l.getCantidad() - t.getCantidad(), Integer::sum);
                    }
                }
                case AGREGAR -> {
                    tareas.save(nuevaTarea(nueva, l.getProducto(), l.getCantidad(), l.getUbicacion(),
                            l.getDescripcion(), orden++));
                    if (l.getProducto() != null) {
                        material.merge(l.getProducto(), l.getCantidad(), Integer::sum);
                    }
                }
            }
        }
        tareas.flush();
        copiarInstrucciones(anterior, nueva, tareas.vigentesDelProyecto(p.getId()));

        CotizacionExtra extra = null;
        if (Boolean.TRUE.equals(c.getRequiereMaterial())) {
            extra = extras.crear(p.getCotizacion(), "Cambio en sitio: " + c.getDescripcion(),
                    OrigenExtra.CAMBIO_INSTRUCCION, material, jefe);
        }

        // El cambio se escribe una sola vez al resolverse (el trigger impide tocarlo después).
        c.setEstado(EstadoCambio.CONFIRMADO);
        c.setConfirmadoPor(jefe);
        c.setFechaConfirmacion(OffsetDateTime.now());
        c.setInstruccionAnterior(anterior);
        c.setInstruccionNueva(nueva);
        c.setExtra(extra);
        c.setUpdatedAt(OffsetDateTime.now());
        cambios.saveAndFlush(c);

        eventos.save(new ProyectoEvento(p, "CAMBIO_CONFIRMADO", jefe, "Instrucciones v." + nueva.getNumeroVersion()
                + ": " + c.getDescripcion() + (extra != null ? " (extra de materiales " + extra.getNumero() + ")" : "")));
        tareasEquipo.alResolver(OrigenTareaEquipo.CAMBIO_PROPUESTO, c.getId(), jefe);
        return proyectos.detalle(p);
    }

    /** Solo jefes. Queda registrado con su motivo; no crea versión ni extra. */
    @Transactional
    public ProyectoDetalle rechazar(UUID cambioId, RechazoCambioRequest req, Usuario jefe) {
        Proyecto p = proyectos.abierto(proyectoDe(cambioId));
        CambioInstruccion c = buscar(cambioId);
        pendiente(c, req.version());
        c.setEstado(EstadoCambio.RECHAZADO);
        c.setConfirmadoPor(jefe);
        c.setFechaConfirmacion(OffsetDateTime.now());
        c.setMotivoRechazo(req.motivo().trim());
        c.setUpdatedAt(OffsetDateTime.now());
        cambios.saveAndFlush(c);
        eventos.save(new ProyectoEvento(p, "CAMBIO_RECHAZADO", jefe, c.getDescripcion() + " — " + c.getMotivoRechazo()));
        tareasEquipo.alResolver(OrigenTareaEquipo.CAMBIO_PROPUESTO, c.getId(), jefe);
        return proyectos.detalle(p);
    }

    // ---------- auxiliares ----------

    /**
     * Contenido de la versión nueva: lo vigente agrupado por producto (o por descripción si es una tarea
     * libre), comparado con la versión anterior para marcar qué se agregó, cambió o quitó.
     */
    private void copiarInstrucciones(InstruccionVersion anterior, InstruccionVersion nueva, List<ChecklistTarea> vigentes) {
        Map<String, Integer> ahora = new LinkedHashMap<>();
        Map<String, ProductoCatalogo> producto = new HashMap<>();
        Map<String, String> libre = new HashMap<>();
        for (ChecklistTarea t : vigentes) {
            String clave = t.getProducto() != null ? "p:" + t.getProducto().getId() : "d:" + t.getDescripcion();
            ahora.merge(clave, t.getCantidad() != null ? t.getCantidad() : 1, Integer::sum);
            if (t.getProducto() != null) {
                producto.put(clave, t.getProducto());
            } else {
                libre.put(clave, t.getDescripcion());
            }
        }
        int orden = 0;
        Set<String> vistas = new HashSet<>();
        for (InstruccionItem previo : items.findByInstruccionVersionIdOrderByOrdenAsc(anterior.getId())) {
            if ("QUITADO".equals(previo.getAccion())) {
                continue;
            }
            String clave = previo.getProducto() != null ? "p:" + previo.getProducto().getId() : "d:" + previo.getDescripcion();
            vistas.add(clave);
            Integer cantidad = ahora.get(clave);
            InstruccionItem i = new InstruccionItem();
            i.setInstruccionVersion(nueva);
            i.setOrden(orden++);
            i.setProducto(previo.getProducto());
            i.setOrigenItemId(previo.getId());
            if (cantidad == null) {
                i.setCantidad(previo.getCantidad());
                i.setDescripcion(previo.getDescripcion());
                i.setAccion("QUITADO");
            } else {
                i.setCantidad(cantidad);
                i.setDescripcion(previo.getProducto() != null
                        ? recortar("Instalar " + cantidad + " × " + previo.getProducto().getNombre())
                        : previo.getDescripcion());
                i.setAccion(cantidad.equals(previo.getCantidad()) ? "SIN_CAMBIO" : "MODIFICADO");
            }
            items.save(i);
        }
        for (var e : ahora.entrySet()) {
            if (vistas.contains(e.getKey())) {
                continue;
            }
            ProductoCatalogo prod = producto.get(e.getKey());
            InstruccionItem i = new InstruccionItem();
            i.setInstruccionVersion(nueva);
            i.setOrden(orden++);
            i.setProducto(prod);
            i.setCantidad(e.getValue());
            i.setDescripcion(prod != null ? recortar("Instalar " + e.getValue() + " × " + prod.getNombre())
                    : libre.get(e.getKey()));
            i.setAccion("AGREGADO");
            items.save(i);
        }
    }

    private static ChecklistTarea nuevaTarea(InstruccionVersion v, ProductoCatalogo producto, int cantidad,
                                             InformeTecnicoUbicacion ubicacion, String nota, int orden) {
        ChecklistTarea t = new ChecklistTarea();
        t.setInstruccionVersion(v);
        if (producto != null) {
            t.setProducto(producto);
            t.setCantidad(cantidad);
            t.setDescripcion(recortar("Instalar " + cantidad + " × " + producto.getNombre()
                    + (nota != null ? " — " + nota : "")));
        } else {
            t.setDescripcion(recortar(nota != null ? nota : "Tarea nueva"));
        }
        t.setUbicacion(ubicacion);
        t.setOrden(orden);
        return t;
    }

    private ChecklistTarea tareaPendiente(Proyecto p, UUID tareaId) {
        if (tareaId == null) {
            throw new BadRequestException("Indica la tarea que cambia");
        }
        ChecklistTarea t = tareas.findById(tareaId)
                .filter(x -> x.getInstruccionVersion().getProyecto().getId().equals(p.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("La tarea no es de este proyecto"));
        if (!Boolean.TRUE.equals(t.getVigente())) {
            throw new ConflictException("Esa tarea ya fue reemplazada por una versión nueva de las instrucciones");
        }
        if (t.getEstado() != EstadoTarea.PENDIENTE) {
            throw new ConflictException("Solo se cambian o quitan tareas pendientes; \"" + t.getDescripcion()
                    + "\" ya está cerrada. Para instalar más, agrega una tarea nueva.");
        }
        return t;
    }

    private static InformeTecnicoUbicacion ubicacion(Proyecto p, UUID id) {
        return p.getCotizacion().getInforme().getUbicaciones().stream()
                .filter(u -> u.getId().equals(id)).findFirst().orElseThrow();
    }

    private InstruccionVersion vigente(Proyecto p) {
        return versiones.findByProyectoIdOrderByNumeroVersionDesc(p.getId()).stream().findFirst()
                .orElseThrow(() -> new ConflictException("El proyecto no tiene instrucciones"));
    }

    private UUID proyectoDe(UUID cambioId) {
        return cambios.proyectoDe(cambioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cambio de instrucciones no encontrado: " + cambioId));
    }

    private CambioInstruccion buscar(UUID id) {
        return cambios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cambio de instrucciones no encontrado: " + id));
    }

    private static void pendiente(CambioInstruccion c, Long version) {
        if (version != null && !version.equals(c.getVersion())) {
            throw new ConflictException("Alguien más resolvió o modificó este cambio. Recarga y vuelve a intentar.");
        }
        if (c.getEstado() != EstadoCambio.PENDIENTE_CONFIRMACION) {
            throw new ConflictException("Este cambio ya fue " + (c.getEstado() == EstadoCambio.CONFIRMADO
                    ? "confirmado" : "rechazado"));
        }
    }

    private static String recortar(String s) {
        return s.length() <= 300 ? s : s.substring(0, 297) + "...";
    }
}
