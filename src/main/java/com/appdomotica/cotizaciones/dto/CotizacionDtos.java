package com.appdomotica.cotizaciones.dto;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.entity.CotizacionExtra;
import com.appdomotica.cotizaciones.entity.CotizacionExtraItem;
import com.appdomotica.cotizaciones.entity.CotizacionItem;
import com.appdomotica.cotizaciones.entity.EstadoExtra;
import com.appdomotica.cotizaciones.entity.OrigenExtra;
import com.appdomotica.cotizaciones.entity.EstadoCotizacion;
import com.appdomotica.cotizaciones.entity.InformeTecnico;
import com.appdomotica.cotizaciones.entity.InformeTecnicoItem;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs de informe técnico y cotización. */
public final class CotizacionDtos {
    private CotizacionDtos() {
    }

    // ---------- Informe técnico ----------

    public record ItemInformeRequest(@NotNull UUID productoId, @NotNull @Positive Integer cantidad) {
    }

    /**
     * `fotoDe`: al crear una versión nueva, ubicación de una versión anterior cuya foto se copia
     * (vacío = sin foto, o se sube una nueva después).
     */
    public record UbicacionRequest(
            UUID fotoDe,
            @Size(max = 2000, message = "La nota no puede pasar de 2000 caracteres") String nota,
            @NotEmpty(message = "Cada ubicación necesita al menos un producto") List<@Valid ItemInformeRequest> items,
            /** Checklist opcional de la ubicación, en orden. Vacía o ausente = una tarea por producto, como siempre. */
            @Size(max = 50, message = "La checklist de una ubicación admite como mucho 50 puntos")
            List<@NotBlank(message = "Un punto de la checklist no puede quedar vacío")
                 @Size(max = 300, message = "Cada punto de la checklist admite como mucho 300 caracteres") String> checklist) {
        /** Ubicación sin checklist. */
        public UbicacionRequest(UUID fotoDe, String nota, List<ItemInformeRequest> items) {
            this(fotoDe, nota, items, null);
        }
    }

    public record InformeRequest(
            @NotNull(message = "El lead es obligatorio") UUID leadId,
            String notas,
            @NotEmpty(message = "El informe necesita al menos una ubicación") List<@Valid UbicacionRequest> ubicaciones,
            UUID cotizacionId) {
        /** Informe nuevo, sin cotización previa. */
        public InformeRequest(UUID leadId, String notas, List<UbicacionRequest> ubicaciones) {
            this(leadId, notas, ubicaciones, null);
        }
    }

    /** Nueva versión de un informe: contenido completo; el lead es el mismo de la versión de origen. */
    public record VersionInformeRequest(
            String notas,
            @NotEmpty(message = "El informe necesita al menos una ubicación") List<@Valid UbicacionRequest> ubicaciones) {
    }

    /** Una versión del informe y su cotización (si tiene). */
    public record VersionResumen(UUID id, int numeroVersion, OffsetDateTime fecha, UUID cotizacionId,
                                 String cotizacionNumero, EstadoCotizacion cotizacionEstado, Integer cotizacionRevision) {
    }

    public record ItemInformeResponse(UUID itemId, UUID productoId, String productoNombre, CategoriaProducto categoria,
                                      int cantidad) {
        static ItemInformeResponse de(InformeTecnicoItem i) {
            return new ItemInformeResponse(i.getId(), i.getProducto().getId(), i.getProducto().getNombre(),
                    i.getProducto().getCategoria(), i.getCantidad());
        }
    }

    /** `fotoId`: foto con las marcas ya dibujadas, descargable en /api/v1/archivos/{id}/descargar. */
    /** `checklist`: puntos de la checklist de la ubicación, en orden (vacía si no tiene). */
    public record UbicacionResponse(UUID id, String nota, UUID fotoId, List<ItemInformeResponse> items,
                                    List<String> checklist) {
        static UbicacionResponse de(InformeTecnicoUbicacion u, Map<UUID, UUID> fotoPorUbicacion) {
            return new UbicacionResponse(u.getId(), u.getNota(), fotoPorUbicacion.get(u.getId()),
                    u.getItems().stream().map(ItemInformeResponse::de).toList(),
                    u.getChecklist().stream().map(c -> c.getTexto()).toList());
        }
    }

    /** Cantidad total de un producto sumando todas las ubicaciones; es lo que pasa a la cotización. */
    public record TotalProductoResponse(UUID productoId, String productoNombre, CategoriaProducto categoria,
                                        int cantidad) {
    }

    /**
     * Una versión del informe. `versiones`: todas las del mismo informe (para elegir cuál ver);
     * `cotizacion`: la de esta versión, si tiene. Las fotos se cambian solo mientras la versión no tenga
     * cotización (`fotosEditables`); después, los cambios van en una versión nueva.
     */
    public record InformeResponse(UUID id, UUID grupoId, int numeroVersion, UUID leadId, UUID clienteId,
                                  String clienteNombre, String ingenieroNombre, String notas, OffsetDateTime fecha,
                                  List<UbicacionResponse> ubicaciones, List<TotalProductoResponse> totales,
                                  VersionResumen cotizacion, List<VersionResumen> versiones, boolean fotosEditables) {
        /** `conProyecto`: la cotización de esta versión ya se inició como proyecto. */
        public static InformeResponse de(InformeTecnico i, Map<UUID, UUID> fotoPorUbicacion,
                                         List<VersionResumen> versiones, boolean conProyecto) {
            VersionResumen propia = versiones.stream().filter(v -> v.id().equals(i.getId())).findFirst().orElse(null);
            VersionResumen cotizacion = propia != null && propia.cotizacionId() != null ? propia : null;
            var cliente = i.getLead().getCliente();
            Map<UUID, TotalProductoResponse> totales = new LinkedHashMap<>();
            for (InformeTecnicoItem it : i.todosLosItems()) {
                var p = it.getProducto();
                totales.merge(p.getId(),
                        new TotalProductoResponse(p.getId(), p.getNombre(), p.getCategoria(), it.getCantidad()),
                        (a, b) -> new TotalProductoResponse(a.productoId(), a.productoNombre(), a.categoria(),
                                a.cantidad() + b.cantidad()));
            }
            return new InformeResponse(i.getId(), i.getGrupoId(), i.getNumeroVersion(), i.getLead().getId(),
                    cliente.getId(), cliente.getNombre(), i.getIngeniero().getNombre(), i.getNotas(), i.getFecha(),
                    i.getUbicaciones().stream().map(u -> UbicacionResponse.de(u, fotoPorUbicacion)).toList(),
                    List.copyOf(totales.values()), cotizacion, versiones,
                    cotizacion == null || (i.isDesdeCotizacion() && !conProyecto));
        }
    }

    // ---------- Cotización: peticiones ----------

    /** Desde un informe técnico (RF-COT-03) o en blanco para un lead (RF-COT-12): uno de los dos. */
    public record NuevaCotizacionRequest(UUID informeId, UUID leadId) {
    }

    /** Ítem armado por la secretaria. Proveedor y precio neto pueden faltar mientras es borrador. */
    public record ItemCotizacionRequest(
            @NotNull UUID productoId,
            UUID proveedorId,
            @NotNull @Positive Integer cantidad,
            @DecimalMin(value = "0.01", message = "El precio neto debe ser mayor a cero") BigDecimal precioNeto,
            UUID productoOriginalId) {
    }

    public record ItemsRequest(@NotNull Long version, @NotEmpty List<@Valid ItemCotizacionRequest> items) {
    }

    public record MargenItem(@NotNull UUID itemId,
                             @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal margenPorcentaje) {
    }

    /** Margen único para todos los ítems y/o márgenes por ítem (los de ítem ganan). */
    public record MargenesRequest(
            @NotNull Long version,
            @DecimalMin("0") @DecimalMax("1000") BigDecimal margenGeneral,
            List<@Valid MargenItem> items) {
    }

    public record IvaRequest(@NotNull Long version,
                             @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentaje) {
    }

    public record AccionRequest(@NotNull Long version) {
    }

    public record RechazoRequest(@NotNull Long version,
                                 @NotBlank(message = "Indica el motivo del rechazo") String motivo) {
    }

    public record IvaDefectoRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal porcentaje) {
    }

    // ---------- Cotización: respuestas ----------

    public record ItemCotizacionResponse(UUID id, UUID productoId, String productoNombre, CategoriaProducto categoria,
                                         UUID proveedorId, String proveedorNombre, int cantidad, BigDecimal precioNeto,
                                         BigDecimal margenPorcentaje, BigDecimal precioVenta, BigDecimal subtotal,
                                         BigDecimal montoIva, BigDecimal subtotalConIva, UUID productoOriginalId,
                                         String productoOriginalNombre) {
        static ItemCotizacionResponse de(CotizacionItem i) {
            return new ItemCotizacionResponse(i.getId(), i.getProducto().getId(), i.getProductoNombre(),
                    i.getProducto().getCategoria(),
                    i.getProveedor() != null ? i.getProveedor().getId() : null,
                    i.getProveedor() != null ? i.getProveedor().getNombre() : null,
                    i.getCantidad(), i.getPrecioNeto(), i.getMargenPorcentaje(), i.getPrecioVenta(), i.getSubtotal(),
                    i.getMontoIva(), i.getSubtotalConIva(),
                    i.getProductoOriginal() != null ? i.getProductoOriginal().getId() : null,
                    i.getProductoOriginalNombre());
        }

        static ItemCotizacionResponse deExtra(CotizacionExtraItem i) {
            return new ItemCotizacionResponse(i.getId(), i.getProducto().getId(), i.getProductoNombre(),
                    i.getProducto().getCategoria(),
                    i.getProveedor() != null ? i.getProveedor().getId() : null,
                    i.getProveedor() != null ? i.getProveedor().getNombre() : null,
                    i.getCantidad(), i.getPrecioNeto(), i.getMargenPorcentaje(), i.getPrecioVenta(), i.getSubtotal(),
                    i.getMontoIva(), i.getSubtotalConIva(), null, null);
        }
    }

    /**
     * Extra de materiales (RF-COT-10). `cambioDescripcion`: el cambio de instrucciones que lo originó,
     * si nació en sitio.
     */
    public record ExtraResponse(UUID id, int numero, String motivo, EstadoExtra estado, OrigenExtra origen,
                                String creadoPor, OffsetDateTime creadoEn, String margenDefinidoPor,
                                String confirmadoPor, OffsetDateTime confirmadoEn, String descartadoPor,
                                OffsetDateTime descartadoEn, String motivoDescarte, BigDecimal ivaPorcentaje,
                                BigDecimal totalSinIva, BigDecimal montoIva, BigDecimal totalConIva,
                                List<ItemCotizacionResponse> items, long version) {
        public static ExtraResponse de(CotizacionExtra e) {
            return new ExtraResponse(e.getId(), e.getNumero(), e.getMotivo(), e.getEstado(), e.getOrigen(),
                    e.getCreadoPor() != null ? e.getCreadoPor().getNombre() : null, e.getCreatedAt(),
                    e.getMargenDefinidoPor() != null ? e.getMargenDefinidoPor().getNombre() : null,
                    e.getConfirmadoPor() != null ? e.getConfirmadoPor().getNombre() : null, e.getFecha(),
                    e.getDescartadoPor() != null ? e.getDescartadoPor().getNombre() : null, e.getDescartadoEn(),
                    e.getMotivoDescarte(), e.getIvaPorcentaje(), e.getTotalExtra(), e.getMontoIva(),
                    e.getTotalConIva(), e.getItems().stream().map(ItemCotizacionResponse::deExtra).toList(),
                    e.getVersion() == null ? 0 : e.getVersion());
        }
    }

    /** Lo que se le cobra al cliente: la cotización más los extras confirmados (RF-COT-11). */
    public record TotalFacturado(BigDecimal totalSinIva, BigDecimal montoIva, BigDecimal totalConIva) {
        static TotalFacturado de(Cotizacion c) {
            BigDecimal sin = c.getTotalSinIva(), iva = c.getMontoIva(), con = c.getTotalConIva();
            for (CotizacionExtra e : c.getExtras()) {
                if (e.getEstado() == EstadoExtra.CONFIRMADO) {
                    sin = sin.add(e.getTotalExtra());
                    iva = iva.add(e.getMontoIva());
                    con = con.add(e.getTotalConIva());
                }
            }
            return new TotalFacturado(sin, iva, con);
        }
    }

    public record NuevoExtraRequest(@NotBlank(message = "Indica para qué es el material extra")
                                    @Size(max = 2000) String motivo) {
    }

    public record DescarteRequest(@NotNull Long version,
                                  @NotBlank(message = "Indica por qué se descarta") @Size(max = 2000) String motivo) {
    }

    public record CotizacionResponse(UUID id, String numero, EstadoCotizacion estado, UUID clienteId,
                                     String clienteNombre, String clienteTelefono, UUID informeId, String armadaPor,
                                     String margenDefinidoPor, OffsetDateTime margenDefinidoEn,
                                     BigDecimal ivaPorcentaje, BigDecimal totalSinIva, BigDecimal montoIva,
                                     BigDecimal totalConIva, int validezDias, String notas, OffsetDateTime fecha,
                                     OffsetDateTime fechaEnvio, OffsetDateTime fechaRespuesta, String motivoRechazo,
                                     UUID proyectoId, long version, List<ItemCotizacionResponse> items,
                                     Integer informeVersion, int revision, boolean editable, UUID leadId,
                                     List<ExtraResponse> extras, TotalFacturado totalFacturado) {
        public static CotizacionResponse de(Cotizacion c, UUID proyectoId) {
            return new CotizacionResponse(c.getId(), c.getNumero(), c.getEstado(), c.getCliente().getId(),
                    c.getCliente().getNombre(), c.getCliente().getTelefono(),
                    c.getInforme() != null ? c.getInforme().getId() : null, c.getArmadaPor().getNombre(),
                    c.getMargenDefinidoPor() != null ? c.getMargenDefinidoPor().getNombre() : null,
                    c.getMargenDefinidoEn(), c.getIvaPorcentaje(), c.getTotalSinIva(), c.getMontoIva(),
                    c.getTotalConIva(), c.getValidezDias(), c.getNotas(), c.getFecha(), c.getFechaEnvio(),
                    c.getFechaRespuesta(), c.getMotivoRechazo(), proyectoId, c.getVersion(),
                    c.getItems().stream().map(ItemCotizacionResponse::de).toList(),
                    c.getInforme() != null ? c.getInforme().getNumeroVersion() : null, c.getRevision(),
                    esEditable(c), c.getLead().getId(),
                    c.getExtras().stream().map(ExtraResponse::de).toList(), TotalFacturado.de(c));
        }
    }

    /** Se corrige mientras es borrador o tras un rechazo (para cambiar márgenes y reenviar). */
    public static boolean esEditable(Cotizacion c) {
        return c.getEstado() == EstadoCotizacion.BORRADOR || c.getEstado() == EstadoCotizacion.RECHAZADA;
    }

    public record CotizacionResumen(UUID id, String numero, EstadoCotizacion estado, String clienteNombre,
                                    BigDecimal totalConIva, int cantidadItems, OffsetDateTime fecha,
                                    UUID informeGrupoId, Integer informeVersion, int revision, UUID proyectoId) {
        public static CotizacionResumen de(Cotizacion c, UUID proyectoId) {
            return new CotizacionResumen(c.getId(), c.getNumero(), c.getEstado(), c.getCliente().getNombre(),
                    c.getTotalConIva(), c.getItems().size(), c.getFecha(),
                    c.getInforme() != null ? c.getInforme().getGrupoId() : null,
                    c.getInforme() != null ? c.getInforme().getNumeroVersion() : null, c.getRevision(), proyectoId);
        }
    }

    public record IvaDefectoResponse(BigDecimal porcentaje) {
    }
}
