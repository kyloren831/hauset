package com.appdomotica.cotizaciones.service;

import com.appdomotica.catalogo.entity.HistorialPrecio;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.HistorialPrecioRepository;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.*;
import com.appdomotica.cotizaciones.entity.*;
import com.appdomotica.cotizaciones.repository.CotizacionExtraRepository;
import com.appdomotica.cotizaciones.repository.CotizacionRepository;
import com.appdomotica.proyectos.entity.ProyectoEvento;
import com.appdomotica.proyectos.repository.ProyectoEventoRepository;
import com.appdomotica.proyectos.repository.ProyectoRepository;
import com.appdomotica.tareas.entity.OrigenTareaEquipo;
import com.appdomotica.tareas.service.TareaEquipoService;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extras de materiales (RF-COT-10/11, RN-05): adenda a una cotización aceptada, sin documento nuevo
 * para el cliente.
 * <ul>
 *   <li>Nace a mano desde la cotización o de un cambio de instrucciones que requiere material (RF-PRY-06).</li>
 *   <li>Mientras está pendiente, jefes y secretaria ponen proveedor y precio neto; el margen lo fija un jefe.</li>
 *   <li>Solo un jefe lo confirma (y entonces suma al total facturado) o lo descarta con un motivo.</li>
 *   <li>Usa el IVA de su cotización y la misma regla de cálculo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExtraMaterialesService {
    private final CotizacionRepository cotizaciones;
    private final CotizacionExtraRepository extras;
    private final ProductoCatalogoRepository productos;
    private final ProveedorRepository proveedores;
    private final HistorialPrecioRepository historial;
    private final ProyectoRepository proyectos;
    private final ProyectoEventoRepository eventos;
    private final TareaEquipoService tareasEquipo;

    /** Extra creado por la oficina sobre una cotización aceptada. */
    @Transactional
    public CotizacionResponse crear(UUID cotizacionId, NuevoExtraRequest req, Usuario usuario) {
        Cotizacion c = cotizaciones.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada: " + cotizacionId));
        crear(c, req.motivo(), OrigenExtra.MANUAL, Map.of(), usuario);
        return respuesta(c);
    }

    /**
     * Crea un extra pendiente. [cantidades]: productos y cuántos (lo que un cambio en sitio agregó); sin
     * precios, que se ponen después.
     */
    @Transactional
    public CotizacionExtra crear(Cotizacion c, String motivo, OrigenExtra origen,
                                 Map<ProductoCatalogo, Integer> cantidades, Usuario usuario) {
        if (c.getEstado() != EstadoCotizacion.ACEPTADA) {
            throw new ConflictException("Los extras de materiales solo se agregan a una cotización aceptada");
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new BadRequestException("Indica para qué es el material extra");
        }
        CotizacionExtra e = new CotizacionExtra();
        e.setCotizacion(c);
        e.setNumero(extras.siguienteNumero(c.getId()));
        e.setMotivo(limpio);
        e.setOrigen(origen);
        e.setCreadoPor(usuario);
        e.setIvaPorcentaje(c.getIvaPorcentaje());
        int orden = 0;
        for (var entrada : cantidades.entrySet()) {
            CotizacionExtraItem i = new CotizacionExtraItem();
            i.setExtra(e);
            i.setProducto(entrada.getKey());
            i.setProductoNombre(entrada.getKey().getNombre());
            i.setCantidad(entrada.getValue());
            i.setOrden(orden++);
            e.getItems().add(i);
        }
        recalcular(e);
        e = extras.saveAndFlush(e);
        c.getExtras().add(e);
        tareasEquipo.alCrearExtra(c, e.getId(), e.getNumero(), limpio, usuario);
        evento(c, "EXTRA_CREADO", usuario, "Extra " + e.getNumero() + ": " + limpio);
        return e;
    }

    /** Productos, proveedor y precio neto. Cambiar los ítems obliga a que un jefe vuelva a fijar el margen. */
    @Transactional
    public CotizacionResponse actualizarItems(UUID extraId, ItemsRequest req) {
        CotizacionExtra e = pendiente(extraId, req.version());
        Map<UUID, BigDecimal> margenPrevio = e.getItems().stream()
                .filter(i -> i.getMargenPorcentaje() != null)
                .collect(Collectors.toMap(i -> i.getProducto().getId(), CotizacionExtraItem::getMargenPorcentaje,
                        (a, b) -> a));
        Set<UUID> vistos = new HashSet<>();
        List<CotizacionExtraItem> nuevos = new ArrayList<>();
        int orden = 0;
        for (ItemCotizacionRequest r : req.items()) {
            if (!vistos.add(r.productoId())) {
                throw new BadRequestException("Un producto aparece dos veces; usa una sola línea con la cantidad total");
            }
            ProductoCatalogo producto = productos.findById(r.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + r.productoId()));
            CotizacionExtraItem i = new CotizacionExtraItem();
            i.setExtra(e);
            i.setProducto(producto);
            i.setProductoNombre(producto.getNombre());
            i.setCantidad(r.cantidad());
            i.setOrden(orden++);
            if (r.proveedorId() != null) {
                Proveedor proveedor = proveedores.findById(r.proveedorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado: " + r.proveedorId()));
                CotizacionService.validarProveedor(proveedor, producto);
                i.setProveedor(proveedor);
            }
            i.setPrecioNeto(r.precioNeto() == null ? null : CalculadoraCotizacion.redondear(r.precioNeto()));
            i.setMargenPorcentaje(margenPrevio.get(producto.getId()));
            nuevos.add(i);
        }
        e.getItems().clear();
        extras.flush();
        e.getItems().addAll(nuevos);
        e.setMargenDefinidoPor(null);
        e.setMargenDefinidoEn(null);
        recalcular(e);
        extras.saveAndFlush(e);
        return respuesta(e.getCotizacion());
    }

    /** Solo jefes (RN-03). */
    @Transactional
    public CotizacionResponse definirMargenes(UUID extraId, MargenesRequest req, Usuario jefe) {
        CotizacionExtra e = pendiente(extraId, req.version());
        if (req.margenGeneral() == null && (req.items() == null || req.items().isEmpty())) {
            throw new BadRequestException("Indica un margen general o márgenes por ítem");
        }
        if (e.getItems().isEmpty()) {
            throw new ConflictException("El extra no tiene productos");
        }
        Map<UUID, CotizacionExtraItem> porId = e.getItems().stream()
                .collect(Collectors.toMap(CotizacionExtraItem::getId, Function.identity()));
        if (req.margenGeneral() != null) {
            e.getItems().forEach(i -> i.setMargenPorcentaje(CalculadoraCotizacion.redondear(req.margenGeneral())));
        }
        if (req.items() != null) {
            for (MargenItem m : req.items()) {
                CotizacionExtraItem i = porId.get(m.itemId());
                if (i == null) {
                    throw new BadRequestException("El ítem " + m.itemId() + " no pertenece a este extra");
                }
                i.setMargenPorcentaje(CalculadoraCotizacion.redondear(m.margenPorcentaje()));
            }
        }
        if (e.getItems().stream().anyMatch(i -> i.getMargenPorcentaje() == null)) {
            throw new BadRequestException("Todos los ítems necesitan margen");
        }
        e.setMargenDefinidoPor(jefe);
        e.setMargenDefinidoEn(OffsetDateTime.now());
        recalcular(e);
        extras.saveAndFlush(e);
        return respuesta(e.getCotizacion());
    }

    /** Un jefe lo confirma: queda fijo y suma al total facturado (RF-COT-11). */
    @Transactional
    public CotizacionResponse confirmar(UUID extraId, AccionRequest req, Usuario jefe) {
        CotizacionExtra e = pendiente(extraId, req.version());
        if (e.getItems().isEmpty()) {
            throw new ConflictException("El extra no tiene productos");
        }
        for (CotizacionExtraItem i : e.getItems()) {
            if (i.getProveedor() == null || i.getPrecioNeto() == null) {
                throw new ConflictException("Falta proveedor o precio neto en: " + i.getProductoNombre());
            }
            if (i.getMargenPorcentaje() == null) {
                throw new ConflictException("Falta el margen de: " + i.getProductoNombre());
            }
        }
        if (e.getMargenDefinidoPor() == null) {
            throw new ConflictException("Un jefe debe confirmar los márgenes del extra");
        }
        recalcular(e);
        e.setEstado(EstadoExtra.CONFIRMADO);
        e.setConfirmadoPor(jefe);
        e.setFecha(OffsetDateTime.now());
        extras.saveAndFlush(e);
        // Los precios del extra también alimentan el historial de referencia (RF-CAT-04).
        for (CotizacionExtraItem i : e.getItems()) {
            historial.save(HistorialPrecio.builder()
                    .producto(i.getProducto())
                    .proveedor(i.getProveedor())
                    .precioNeto(i.getPrecioNeto())
                    .disponible(true)
                    .registradoPor(jefe)
                    .fecha(LocalDateTime.now())
                    .build());
        }
        tareasEquipo.alResolver(OrigenTareaEquipo.EXTRA_PENDIENTE, e.getId(), jefe);
        evento(e.getCotizacion(), "EXTRA_CONFIRMADO", jefe, "Extra " + e.getNumero() + " confirmado");
        return respuesta(e.getCotizacion());
    }

    /** Un jefe lo descarta (por ejemplo, el material ya estaba en bodega): no suma y queda en el historial. */
    @Transactional
    public CotizacionResponse descartar(UUID extraId, DescarteRequest req, Usuario jefe) {
        CotizacionExtra e = pendiente(extraId, req.version());
        e.setEstado(EstadoExtra.DESCARTADO);
        e.setDescartadoPor(jefe);
        e.setDescartadoEn(OffsetDateTime.now());
        e.setMotivoDescarte(req.motivo().trim());
        extras.saveAndFlush(e);
        tareasEquipo.alResolver(OrigenTareaEquipo.EXTRA_PENDIENTE, e.getId(), jefe);
        evento(e.getCotizacion(), "EXTRA_DESCARTADO", jefe, "Extra " + e.getNumero() + " descartado: "
                + e.getMotivoDescarte());
        return respuesta(e.getCotizacion());
    }

    // ---------- auxiliares ----------

    static void recalcular(CotizacionExtra e) {
        List<CalculadoraCotizacion.Linea> lineas = new ArrayList<>();
        BigDecimal iva = e.getIvaPorcentaje() != null ? e.getIvaPorcentaje() : e.getCotizacion().getIvaPorcentaje();
        for (CotizacionExtraItem i : e.getItems()) {
            if (i.getPrecioNeto() != null && i.getMargenPorcentaje() != null) {
                var l = CalculadoraCotizacion.linea(i.getPrecioNeto(), i.getMargenPorcentaje(), i.getCantidad(), iva);
                i.setPrecioVenta(l.precioVenta());
                i.setSubtotal(l.subtotal());
                i.setMontoIva(l.montoIva());
                i.setSubtotalConIva(l.subtotalConIva());
                lineas.add(l);
            } else {
                i.setPrecioVenta(null);
                i.setSubtotal(null);
                i.setMontoIva(null);
                i.setSubtotalConIva(null);
            }
        }
        var t = CalculadoraCotizacion.totales(lineas);
        e.setTotalExtra(t.totalSinIva());
        e.setMontoIva(t.montoIva());
        e.setTotalConIva(t.totalConIva());
    }

    private CotizacionExtra pendiente(UUID id, Long version) {
        CotizacionExtra e = extras.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Extra no encontrado: " + id));
        if (version != null && !version.equals(e.getVersion())) {
            throw new ConflictException("Alguien más modificó el extra. Recarga y vuelve a intentar.");
        }
        if (e.getEstado() != EstadoExtra.PENDIENTE) {
            throw new ConflictException("El extra ya está " + e.getEstado().name().toLowerCase(Locale.ROOT)
                    + " y no se modifica");
        }
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    /** Si la cotización ya es proyecto, el movimiento queda también en su bitácora. */
    private void evento(Cotizacion c, String tipo, Usuario usuario, String detalle) {
        proyectos.findByCotizacionId(c.getId())
                .ifPresent(p -> eventos.save(new ProyectoEvento(p, tipo, usuario, detalle)));
    }

    private CotizacionResponse respuesta(Cotizacion c) {
        UUID proyectoId = proyectos.findByCotizacionId(c.getId()).map(p -> p.getId()).orElse(null);
        return CotizacionResponse.de(c, proyectoId);
    }
}
