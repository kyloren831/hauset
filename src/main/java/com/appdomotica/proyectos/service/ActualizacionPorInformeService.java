package com.appdomotica.proyectos.service;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.entity.CotizacionExtra;
import com.appdomotica.cotizaciones.entity.CotizacionItem;
import com.appdomotica.cotizaciones.entity.InformeTecnico;
import com.appdomotica.cotizaciones.entity.InformeTecnicoItem;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import com.appdomotica.cotizaciones.entity.InformeUbicacionChecklistItem;
import com.appdomotica.cotizaciones.entity.OrigenExtra;
import com.appdomotica.cotizaciones.repository.CotizacionRepository;
import com.appdomotica.cotizaciones.repository.InformeTecnicoRepository;
import com.appdomotica.cotizaciones.service.ExtraMaterialesService;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.repository.*;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Versión nueva del informe técnico de una cotización aceptada (RF-COT-22). La cotización no se recotiza:
 * <ul>
 *   <li>la versión nueva pasa a ser el informe de esa cotización; la anterior queda como historial;</li>
 *   <li>el material que la versión nueva agrega respecto de la anterior queda como extra pendiente, que la
 *       oficina cotiza y un jefe confirma (RF-COT-20);</li>
 *   <li>si ya hay proyecto, sus instrucciones pasan a una versión nueva: tareas y checklist se ajustan
 *       ubicación por ubicación. Lo que ya se hizo no se borra ni se quita (RN-06).</li>
 * </ul>
 * Cada ubicación se sigue entre versiones por la ubicación de la que sale (`origen_ubicacion_id`); una sin
 * origen se trata como nueva.
 */
@Service
@RequiredArgsConstructor
public class ActualizacionPorInformeService {
    private final CotizacionRepository cotizaciones;
    private final InformeTecnicoRepository informes;
    private final ProyectoRepository proyectos;
    private final InstruccionVersionRepository versiones;
    private final ChecklistTareaRepository tareas;
    private final ProyectoChecklistItemRepository checklist;
    private final ProyectoEventoRepository eventos;
    private final ExtraMaterialesService extras;
    private final InstruccionesVersionador versionador;
    private final JdbcTemplate jdbc;

    /**
     * La cotización aceptada del informe, si tiene, comprobando que una versión nueva se le puede aplicar:
     * una sola aceptada y su proyecto (si hay) sin cerrar.
     */
    @Transactional(readOnly = true)
    public Optional<Cotizacion> aceptadaParaVersionNueva(UUID grupoInforme) {
        List<Cotizacion> aceptadas = cotizaciones.aceptadasDelInforme(grupoInforme);
        if (aceptadas.isEmpty()) {
            return Optional.empty();
        }
        if (aceptadas.size() > 1) {
            throw new ConflictException("El informe tiene varias cotizaciones aceptadas ("
                    + aceptadas.stream().map(Cotizacion::getNumero).collect(Collectors.joining(", "))
                    + "). Desactiva las que sobran antes de crear otra versión.");
        }
        Cotizacion c = aceptadas.get(0);
        proyectos.findByCotizacionId(c.getId())
                .filter(p -> p.getEstado() == EstadoProyecto.CERRADO)
                .ifPresent(p -> {
                    throw new ConflictException("El proyecto de la cotización " + c.getNumero() + " ya está cerrado. "
                            + "Para un trabajo nuevo, levanta un informe técnico nuevo.");
                });
        return Optional.of(c);
    }

    /** Aplica la versión nueva (ya guardada) a la cotización aceptada y, si tiene, a su proyecto. */
    @Transactional
    public void aplicar(Cotizacion c, InformeTecnico nueva, Usuario quien) {
        InformeTecnico anterior = c.getInforme();
        anterior.setReemplazadaEnCotizacion(c);
        informes.save(anterior);
        c.setInforme(nueva);
        c.setUpdatedAt(OffsetDateTime.now());
        cotizaciones.saveAndFlush(c);

        Productos productos = new Productos(c, anterior, nueva);
        Map<ProductoCatalogo, Integer> material = productos.agregado(anterior, nueva);
        CotizacionExtra extra = material.isEmpty() ? null : extras.crear(c, "Informe técnico v."
                + nueva.getNumeroVersion() + ": material agregado respecto de la v." + anterior.getNumeroVersion(),
                OrigenExtra.INFORME_TECNICO, material, quien);

        Optional<Proyecto> proyecto = proyectos.findByCotizacionId(c.getId());
        if (proyecto.isPresent()) {
            actualizarProyecto(proyectos.bloquear(proyecto.get().getId()).orElseThrow(), anterior, nueva, productos,
                    extra, quien);
        }
    }

    private void actualizarProyecto(Proyecto p, InformeTecnico anterior, InformeTecnico nueva, Productos productos,
                                    CotizacionExtra extra, Usuario quien) {
        seguirUbicaciones(p, nueva);

        Ajuste a = new Ajuste(p, quien);
        Set<UUID> vigentesDelInforme = nueva.getUbicaciones().stream().map(InformeTecnicoUbicacion::getId)
                .collect(Collectors.toSet());
        List<ChecklistTarea> vigentes = tareas.vigentesDelProyecto(p.getId());
        a.orden = vigentes.stream().mapToInt(ChecklistTarea::getOrden).max().orElse(-1) + 1;
        List<ProyectoChecklistItem> puntos = checklist.findByProyectoIdOrderByOrdenAsc(p.getId());

        // Ubicaciones que la versión nueva ya no tiene: se quita lo pendiente; lo hecho se conserva.
        for (ChecklistTarea t : vigentes) {
            if (t.getUbicacion() != null && !vigentesDelInforme.contains(t.getUbicacion().getId())
                    && t.getEstado() == EstadoTarea.PENDIENTE) {
                a.quitar(t);
            }
        }
        for (ProyectoChecklistItem i : puntos) {
            if (!vigentesDelInforme.contains(i.getUbicacion().getId()) && !i.isMarcado()) {
                a.borrarPunto(i);
            }
        }

        for (InformeTecnicoUbicacion u : nueva.getUbicaciones()) {
            List<ChecklistTarea> aqui = vigentes.stream()
                    .filter(t -> t.getUbicacion() != null && t.getUbicacion().getId().equals(u.getId())).toList();
            List<ProyectoChecklistItem> puntosAqui = puntos.stream()
                    .filter(i -> i.getUbicacion().getId().equals(u.getId())).toList();
            if (!u.getChecklist().isEmpty()) {
                // Con checklist no hay tareas por producto (RF-PRY-02).
                aqui.stream().filter(t -> t.getEstado() == EstadoTarea.PENDIENTE && t.getProducto() != null)
                        .forEach(a::quitar);
                sincronizarChecklist(a, u, puntosAqui);
            } else {
                puntosAqui.stream().filter(i -> !i.isMarcado()).forEach(a::borrarPunto);
                ajustarTareas(a, u, aqui, productos);
            }
        }

        if (!a.huboCambios()) {
            eventos.save(new ProyectoEvento(p, "INFORME_ACTUALIZADO", quien, "Informe técnico v."
                    + nueva.getNumeroVersion() + " (antes v." + anterior.getNumeroVersion() + "): sin cambios en "
                    + "tareas ni checklist" + (extra != null ? "; extra de materiales " + extra.getNumero() : "")));
            return;
        }
        tareas.flush();
        // Aunque solo cambie la checklist, queda una versión nueva de instrucciones para el historial.
        versionador.copiarInstrucciones(a.anterior, a.version(), tareas.vigentesDelProyecto(p.getId()));
        eventos.save(new ProyectoEvento(p, "INFORME_ACTUALIZADO", quien, a.resumen(anterior, nueva, extra)));
    }

    /**
     * Mueve a las ubicaciones de la versión nueva todo lo del proyecto que apunta a una ubicación anterior
     * (tareas, puntos de checklist y fotos de antes y después). Se hace en SQL para no cambiar la versión
     * de las tareas: un instalador sin conexión puede seguir cerrándolas.
     */
    private void seguirUbicaciones(Proyecto p, InformeTecnico nueva) {
        List<InformeTecnico> grupo = informes.findByGrupoIdInOrderByNumeroVersionAsc(Set.of(nueva.getGrupoId()));
        Map<UUID, UUID> origen = new HashMap<>();
        for (InformeTecnico i : grupo) {
            for (InformeTecnicoUbicacion u : i.getUbicaciones()) {
                origen.put(u.getId(), u.getOrigenUbicacionId());
            }
        }
        Map<UUID, UUID> destinoPorRaiz = new HashMap<>();
        Set<UUID> nuevas = new HashSet<>();
        for (InformeTecnicoUbicacion u : nueva.getUbicaciones()) {
            nuevas.add(u.getId());
            destinoPorRaiz.putIfAbsent(raiz(u.getId(), origen), u.getId());
        }
        for (UUID vieja : origen.keySet()) {
            UUID destino = destinoPorRaiz.get(raiz(vieja, origen));
            if (nuevas.contains(vieja) || destino == null) {
                continue;
            }
            jdbc.update("update checklist_tareas set ubicacion_id = ? where ubicacion_id = ? and instruccion_version_id"
                    + " in (select id from instrucciones_version where proyecto_id = ?)", destino, vieja, p.getId());
            jdbc.update("update proyecto_checklist_items set ubicacion_id = ? where ubicacion_id = ? and proyecto_id = ?",
                    destino, vieja, p.getId());
            jdbc.update("update archivos_adjuntos set entidad_id = ? where entidad_tipo = ? and entidad_id = ?",
                    destino, ProyectoService.ENTIDAD_UBICACION, vieja);
        }
    }

    /** Primera ubicación de la cadena de la que sale esta. */
    private static UUID raiz(UUID id, Map<UUID, UUID> origen) {
        UUID actual = id;
        Set<UUID> vistas = new HashSet<>();
        while (origen.get(actual) != null && vistas.add(actual)) {
            actual = origen.get(actual);
        }
        return actual;
    }

    /**
     * Ubicación sin checklist: por cada producto, lo que pide la versión nueva contra lo que ya tienen sus
     * tareas vigentes (hechas o no). Si pide más, una tarea nueva por la diferencia; si pide menos, se quitan
     * o achican tareas pendientes. Lo ya instalado de más queda anotado en la bitácora.
     */
    private void ajustarTareas(Ajuste a, InformeTecnicoUbicacion u, List<ChecklistTarea> aqui, Productos productos) {
        Map<UUID, Integer> pedido = new LinkedHashMap<>();
        for (InformeTecnicoItem it : u.getItems()) {
            pedido.merge(productos.traducir(it.getProducto().getId()), it.getCantidad(), Integer::sum);
        }
        Map<UUID, Integer> hay = new LinkedHashMap<>();
        for (ChecklistTarea t : aqui) {
            if (t.getProducto() != null) {
                hay.merge(t.getProducto().getId(), t.getCantidad() != null ? t.getCantidad() : 1, Integer::sum);
            }
        }
        Set<UUID> todos = new LinkedHashSet<>(pedido.keySet());
        todos.addAll(hay.keySet());
        for (UUID prod : todos) {
            int diferencia = pedido.getOrDefault(prod, 0) - hay.getOrDefault(prod, 0);
            if (diferencia > 0) {
                a.agregar(productos.de(prod), diferencia, u);
            } else if (diferencia < 0) {
                List<ChecklistTarea> pendientes = aqui.stream()
                        .filter(t -> t.getProducto() != null && t.getProducto().getId().equals(prod)
                                && t.getEstado() == EstadoTarea.PENDIENTE)
                        .sorted(Comparator.comparing((ChecklistTarea t) -> t.getCantidad() != null ? t.getCantidad() : 1)
                                .reversed())
                        .toList();
                int sobra = -diferencia;
                for (ChecklistTarea t : pendientes) {
                    if (sobra == 0) {
                        break;
                    }
                    int cantidad = t.getCantidad() != null ? t.getCantidad() : 1;
                    a.quitar(t);
                    if (cantidad > sobra) {
                        a.agregar(t.getProducto(), cantidad - sobra, u).setOrigenTareaId(t.getId());
                        sobra = 0;
                    } else {
                        sobra -= cantidad;
                    }
                }
                if (sobra > 0) {
                    a.yaInstalado.add(sobra + " × " + productos.de(prod).getNombre());
                }
            }
        }
    }

    /**
     * Ubicación con checklist: los puntos con el mismo texto se conservan (con su marca); los nuevos se agregan;
     * los que ya no están se borran si nadie los marcó. El orden queda el de la versión nueva.
     */
    private void sincronizarChecklist(Ajuste a, InformeTecnicoUbicacion u, List<ProyectoChecklistItem> actuales) {
        List<ProyectoChecklistItem> libres = new ArrayList<>(actuales);
        int orden = 0;
        for (InformeUbicacionChecklistItem deseado : u.getChecklist()) {
            String texto = normal(deseado.getTexto());
            Optional<ProyectoChecklistItem> igual = libres.stream().filter(i -> normal(i.getTexto()).equals(texto))
                    .findFirst();
            if (igual.isPresent()) {
                libres.remove(igual.get());
                a.reordenar(igual.get(), orden++);
            } else {
                ProyectoChecklistItem nuevo = new ProyectoChecklistItem();
                nuevo.setProyecto(a.proyecto);
                nuevo.setUbicacion(u);
                nuevo.setOrigenItemId(deseado.getId());
                nuevo.setTexto(deseado.getTexto());
                nuevo.setOrden(orden++);
                checklist.save(nuevo);
                a.puntosAgregados++;
            }
        }
        for (ProyectoChecklistItem sobra : libres) {
            if (sobra.isMarcado()) {
                a.reordenar(sobra, orden++);
            } else {
                a.borrarPunto(sobra);
            }
        }
    }

    private static String normal(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Lo que va cambiando en el proyecto; la versión nueva de instrucciones se crea al primer cambio de tareas. */
    private final class Ajuste {
        final Proyecto proyecto;
        final Usuario quien;
        final InstruccionVersion anterior;
        InstruccionVersion version;
        int orden;
        int agregadas;
        int quitadas;
        int puntosAgregados;
        int puntosQuitados;
        boolean reordenado;
        final List<String> yaInstalado = new ArrayList<>();

        Ajuste(Proyecto proyecto, Usuario quien) {
            this.proyecto = proyecto;
            this.quien = quien;
            this.anterior = versiones.findByProyectoIdOrderByNumeroVersionDesc(proyecto.getId()).stream().findFirst()
                    .orElseThrow(() -> new ConflictException("El proyecto no tiene instrucciones"));
        }

        InstruccionVersion version() {
            if (version == null) {
                InstruccionVersion v = new InstruccionVersion();
                v.setProyecto(proyecto);
                v.setNumeroVersion(anterior.getNumeroVersion() + 1);
                v.setGeneradaDesde("INFORME_TECNICO");
                v.setCreadoPor(quien);
                version = versiones.save(v);
            }
            return version;
        }

        ChecklistTarea agregar(ProductoCatalogo producto, int cantidad, InformeTecnicoUbicacion u) {
            ChecklistTarea t = CambioInstruccionService.nuevaTarea(version(), producto, cantidad, u, null, orden++);
            agregadas++;
            return tareas.save(t);
        }

        void quitar(ChecklistTarea t) {
            t.setVigente(false);
            t.setUpdatedAt(OffsetDateTime.now());
            tareas.save(t);
            quitadas++;
        }

        void borrarPunto(ProyectoChecklistItem i) {
            checklist.delete(i);
            puntosQuitados++;
        }

        void reordenar(ProyectoChecklistItem i, int nuevoOrden) {
            if (!Objects.equals(i.getOrden(), nuevoOrden)) {
                i.setOrden(nuevoOrden);
                i.setUpdatedAt(OffsetDateTime.now());
                checklist.save(i);
                reordenado = true;
            }
        }

        boolean huboCambios() {
            return agregadas + quitadas + puntosAgregados + puntosQuitados > 0 || reordenado;
        }

        String resumen(InformeTecnico anteriorInforme, InformeTecnico nueva, CotizacionExtra extra) {
            List<String> partes = new ArrayList<>();
            if (agregadas > 0) {
                partes.add(agregadas + (agregadas == 1 ? " tarea nueva" : " tareas nuevas"));
            }
            if (quitadas > 0) {
                partes.add(quitadas + (quitadas == 1 ? " tarea quitada o ajustada" : " tareas quitadas o ajustadas"));
            }
            if (puntosAgregados > 0) {
                partes.add(puntosAgregados + (puntosAgregados == 1 ? " punto de checklist nuevo"
                        : " puntos de checklist nuevos"));
            }
            if (puntosQuitados > 0) {
                partes.add(puntosQuitados + (puntosQuitados == 1 ? " punto de checklist quitado"
                        : " puntos de checklist quitados"));
            }
            if (partes.isEmpty()) {
                partes.add("checklist reordenada");
            }
            String texto = "Informe técnico v." + nueva.getNumeroVersion() + " (antes v."
                    + anteriorInforme.getNumeroVersion() + "): instrucciones v." + version.getNumeroVersion() + ", "
                    + String.join(", ", partes);
            if (!yaInstalado.isEmpty()) {
                texto += ". Ya instalado de más (no se quita): " + String.join(", ", yaInstalado);
            }
            if (extra != null) {
                texto += ". Extra de materiales " + extra.getNumero() + " pendiente";
            }
            return texto;
        }
    }

    /**
     * Productos del informe tal como se cotizaron: si la cotización reemplazó un producto por otro
     * (RF-COT-14), el del informe se traduce al que se cotizó.
     */
    private static final class Productos {
        private final Map<UUID, UUID> reemplazo = new HashMap<>();
        private final Map<UUID, ProductoCatalogo> porId = new HashMap<>();

        Productos(Cotizacion c, InformeTecnico... informesDelCambio) {
            for (CotizacionItem i : c.getItems()) {
                porId.put(i.getProducto().getId(), i.getProducto());
                if (i.getProductoOriginal() != null) {
                    reemplazo.putIfAbsent(i.getProductoOriginal().getId(), i.getProducto().getId());
                }
            }
            for (InformeTecnico informe : informesDelCambio) {
                for (InformeTecnicoItem it : informe.todosLosItems()) {
                    porId.putIfAbsent(it.getProducto().getId(), it.getProducto());
                }
            }
        }

        UUID traducir(UUID producto) {
            return reemplazo.getOrDefault(producto, producto);
        }

        ProductoCatalogo de(UUID id) {
            return porId.get(id);
        }

        /** Lo que la versión nueva pide de más respecto de la anterior, producto por producto. */
        Map<ProductoCatalogo, Integer> agregado(InformeTecnico anterior, InformeTecnico nueva) {
            Map<UUID, Integer> diferencia = new LinkedHashMap<>();
            for (InformeTecnicoItem it : nueva.todosLosItems()) {
                diferencia.merge(traducir(it.getProducto().getId()), it.getCantidad(), Integer::sum);
            }
            for (InformeTecnicoItem it : anterior.todosLosItems()) {
                diferencia.merge(traducir(it.getProducto().getId()), -it.getCantidad(), Integer::sum);
            }
            Map<ProductoCatalogo, Integer> r = new LinkedHashMap<>();
            diferencia.forEach((id, n) -> {
                if (n > 0) {
                    r.put(de(id), n);
                }
            });
            return r;
        }
    }
}
