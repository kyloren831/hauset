package com.appdomotica.cotizaciones.service;

import com.appdomotica.cotizaciones.dto.CotizacionDtos;

import com.appdomotica.catalogo.entity.HistorialPrecio;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.HistorialPrecioRepository;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.clientes.repository.LeadRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.*;
import com.appdomotica.cotizaciones.entity.*;
import com.appdomotica.cotizaciones.repository.CotizacionRepository;
import com.appdomotica.cotizaciones.repository.InformeTecnicoRepository;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.repository.ChecklistTareaRepository;
import com.appdomotica.proyectos.repository.InstruccionItemRepository;
import com.appdomotica.proyectos.repository.InstruccionVersionRepository;
import com.appdomotica.proyectos.repository.ProyectoRepository;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cotización: borrador → enviada → aceptada | rechazada.
 * <ul>
 *   <li>Cada ítem es una fotografía (proveedor, neto, margen, venta, IVA) que nunca se recalcula
 *       desde el catálogo (RF-COT-04, RN-01).</li>
 *   <li>El margen lo fija un jefe y queda registrado quién (RF-COT-05). Si la secretaria cambia
 *       ítems después, el margen debe volver a confirmarse.</li>
 *   <li>Al enviar: número, PDF guardado e historial de precios (RF-CAT-04, RF-COT-08).</li>
 *   <li>Puede nacer de un informe técnico o en blanco para un lead; el informe se le puede levantar después
 *       (RF-COT-12, RF-COT-16).</li>
 *   <li>Aceptar no crea el proyecto: se inicia aparte, solo con informe (RF-COT-09, RN-11).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CotizacionService {
    static final String ENTIDAD_PDF = "COTIZACION";
    static final String CATEGORIA_PDF = "DOCUMENTO_PDF";

    private final CotizacionRepository cotizaciones;
    private final InformeTecnicoRepository informes;
    private final LeadRepository leads;
    private final ProductoCatalogoRepository productos;
    private final ProveedorRepository proveedores;
    private final HistorialPrecioRepository historial;
    private final ParametroService parametros;
    private final CotizacionPdfService pdf;
    private final ArchivoAdjuntoService archivos;
    private final ProyectoRepository proyectos;
    private final InstruccionVersionRepository instrucciones;
    private final InstruccionItemRepository instruccionItems;
    private final ChecklistTareaRepository tareas;
    private final com.appdomotica.proyectos.repository.ProyectoChecklistItemRepository checklistProyecto;
    private final com.appdomotica.proyectos.repository.ProyectoEventoRepository eventosProyecto;
    private final com.appdomotica.tareas.service.TareaEquipoService tareasEquipo;

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public PageResponse<CotizacionResumen> listar(EstadoCotizacion estado, Pageable pageable) {
        var page = estado == null ? cotizaciones.findAll(pageable) : cotizaciones.findByEstado(estado, pageable);
        Map<UUID, UUID> proyectoPor = proyectosDe(page.getContent());
        return PageResponse.from(page.map(c -> CotizacionResumen.de(c, proyectoPor.get(c.getId()))));
    }

    /** Aceptadas que aún no se iniciaron como proyecto: el aviso para jefes y secretaria (RF-COT-17/18). */
    @Transactional(readOnly = true)
    public List<CotizacionResumen> porIniciar() {
        return cotizaciones.aceptadasSinProyecto().stream().map(c -> CotizacionResumen.de(c, null)).toList();
    }

    private Map<UUID, UUID> proyectosDe(List<Cotizacion> lista) {
        if (lista.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> r = new HashMap<>();
        for (Object[] fila : proyectos.idsPorCotizacion(lista.stream().map(Cotizacion::getId).toList())) {
            r.put((UUID) fila[0], (UUID) fila[1]);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public CotizacionResponse obtener(UUID id) {
        return respuesta(buscar(id));
    }

    // ---------- Armado (borrador) ----------

    @Transactional
    public CotizacionResponse crear(NuevaCotizacionRequest req, Usuario usuario) {
        if ((req.informeId() == null) == (req.leadId() == null)) {
            throw new BadRequestException("Indica el informe técnico o el lead, uno de los dos");
        }
        return req.informeId() != null ? crearDesdeInforme(req.informeId(), usuario) : crearEnBlanco(req.leadId(), usuario);
    }

    /** Borrador vacío para un lead ya registrado; los productos se agregan a mano (RF-COT-12). */
    private CotizacionResponse crearEnBlanco(UUID leadId, Usuario usuario) {
        Lead lead = leads.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado: " + leadId));
        if (lead.getEstado() == EstadoLead.DESCARTADO) {
            throw new ConflictException("El lead está descartado");
        }
        Cotizacion c = new Cotizacion();
        c.setLead(lead);
        c.setCliente(lead.getCliente());
        c.setArmadaPor(usuario);
        c.setIvaPorcentaje(parametros.ivaPorDefecto());
        return respuesta(cotizaciones.save(c));
    }

    /** Crea el borrador copiando productos y cantidades del informe técnico (RF-COT-03). */
    private CotizacionResponse crearDesdeInforme(UUID informeId, Usuario usuario) {
        InformeTecnico informe = informes.findById(informeId)
                .orElseThrow(() -> new ResourceNotFoundException("Informe no encontrado: " + informeId));
        if (cotizaciones.existsByInformeId(informeId)) {
            throw new ConflictException("Esta versión del informe ya tiene cotización; ábrela o crea una versión nueva");
        }
        Cotizacion c = new Cotizacion();
        c.setInforme(informe);
        c.setLead(informe.getLead());
        c.setCliente(informe.getLead().getCliente());
        c.setArmadaPor(usuario);
        c.setIvaPorcentaje(parametros.ivaPorDefecto());
        // Una línea por producto con la cantidad de todas las ubicaciones sumada.
        Map<UUID, CotizacionItem> porProducto = new LinkedHashMap<>();
        for (InformeTecnicoItem i : informe.todosLosItems()) {
            CotizacionItem item = porProducto.get(i.getProducto().getId());
            if (item != null) {
                item.setCantidad(item.getCantidad() + i.getCantidad());
                continue;
            }
            item = new CotizacionItem();
            item.setCotizacion(c);
            item.setProducto(i.getProducto());
            item.setProductoNombre(i.getProducto().getNombre());
            item.setCantidad(i.getCantidad());
            item.setOrden(porProducto.size());
            porProducto.put(i.getProducto().getId(), item);
        }
        c.getItems().addAll(porProducto.values());
        return respuesta(cotizaciones.save(c));
    }

    /**
     * Reemplaza los ítems del borrador. Un ítem puede cambiar de producto (el proveedor no tiene el pedido):
     * con `productoOriginalId` recuerda cuál reemplaza (RF-COT-14/15). Si había un margen confirmado, vuelve a
     * quedar pendiente de un jefe.
     */
    @Transactional
    public CotizacionResponse actualizarItems(UUID id, ItemsRequest req) {
        Cotizacion c = buscarEditable(id, req.version());
        Map<UUID, BigDecimal> margenPrevio = c.getItems().stream()
                .filter(i -> i.getMargenPorcentaje() != null)
                .collect(Collectors.toMap(i -> i.getProducto().getId(), CotizacionItem::getMargenPorcentaje, (a, b) -> a));

        Set<UUID> vistos = new HashSet<>();
        List<CotizacionItem> nuevos = new ArrayList<>();
        int orden = 0;
        for (ItemCotizacionRequest r : req.items()) {
            if (!vistos.add(r.productoId())) {
                throw new BadRequestException("Un producto aparece dos veces; usa una sola línea con la cantidad total");
            }
            ProductoCatalogo producto = productos.findById(r.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + r.productoId()));
            CotizacionItem item = new CotizacionItem();
            item.setCotizacion(c);
            item.setProducto(producto);
            item.setProductoNombre(producto.getNombre());
            item.setCantidad(r.cantidad());
            item.setOrden(orden++);
            if (r.productoOriginalId() != null && !r.productoOriginalId().equals(producto.getId())) {
                ProductoCatalogo original = productos.findById(r.productoOriginalId())
                        .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + r.productoOriginalId()));
                item.setProductoOriginal(original);
                item.setProductoOriginalNombre(original.getNombre());
            }
            if (r.proveedorId() != null) {
                Proveedor proveedor = proveedores.findById(r.proveedorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado: " + r.proveedorId()));
                validarProveedor(proveedor, producto);
                item.setProveedor(proveedor);
            }
            item.setPrecioNeto(r.precioNeto() == null ? null : CalculadoraCotizacion.redondear(r.precioNeto()));
            // Se conserva el margen propuesto, pero el jefe tendrá que confirmarlo de nuevo.
            item.setMargenPorcentaje(margenPrevio.get(producto.getId()));
            nuevos.add(item);
        }
        c.getItems().clear();
        cotizaciones.flush();
        c.getItems().addAll(nuevos);
        c.setMargenDefinidoPor(null);
        c.setMargenDefinidoEn(null);
        recalcular(c);
        return respuesta(cotizaciones.saveAndFlush(c));
    }

    /** Solo jefes: margen general y/o por ítem (RN-03). Queda registrado quién lo definió (RF-COT-05). */
    @Transactional
    public CotizacionResponse definirMargenes(UUID id, MargenesRequest req, Usuario jefe) {
        Cotizacion c = buscarEditable(id, req.version());
        if (req.margenGeneral() == null && (req.items() == null || req.items().isEmpty())) {
            throw new BadRequestException("Indica un margen general o márgenes por ítem");
        }
        Map<UUID, CotizacionItem> porId = c.getItems().stream()
                .collect(Collectors.toMap(CotizacionItem::getId, Function.identity()));
        if (req.margenGeneral() != null) {
            c.getItems().forEach(i -> i.setMargenPorcentaje(CalculadoraCotizacion.redondear(req.margenGeneral())));
        }
        if (req.items() != null) {
            for (MargenItem m : req.items()) {
                CotizacionItem item = porId.get(m.itemId());
                if (item == null) {
                    throw new BadRequestException("El ítem " + m.itemId() + " no pertenece a esta cotización");
                }
                item.setMargenPorcentaje(CalculadoraCotizacion.redondear(m.margenPorcentaje()));
            }
        }
        if (c.getItems().stream().anyMatch(i -> i.getMargenPorcentaje() == null)) {
            throw new BadRequestException("Todos los ítems necesitan margen");
        }
        c.setMargenDefinidoPor(jefe);
        c.setMargenDefinidoEn(OffsetDateTime.now());
        recalcular(c);
        return respuesta(cotizaciones.saveAndFlush(c));
    }

    /** Cambia el IVA de esta cotización (por defecto viene del parámetro del sistema). */
    @Transactional
    public CotizacionResponse definirIva(UUID id, IvaRequest req, Usuario usuario) {
        Cotizacion c = buscarEditable(id, req.version());
        c.setIvaPorcentaje(CalculadoraCotizacion.redondear(req.porcentaje()));
        c.setIvaDefinidoPor(usuario);
        recalcular(c);
        return respuesta(cotizaciones.saveAndFlush(c));
    }

    // ---------- Transiciones ----------

    @Transactional
    public CotizacionResponse enviar(UUID id, AccionRequest req, Usuario usuario) {
        Cotizacion c = buscarEditable(id, req.version());
        if (c.getItems().isEmpty()) {
            throw new ConflictException("La cotización no tiene ítems");
        }
        for (CotizacionItem i : c.getItems()) {
            if (i.getProveedor() == null || i.getPrecioNeto() == null) {
                throw new ConflictException("Falta proveedor o precio neto en: " + i.getProductoNombre());
            }
            if (i.getMargenPorcentaje() == null) {
                throw new ConflictException("Falta el margen de: " + i.getProductoNombre());
            }
        }
        if (c.getMargenDefinidoPor() == null) {
            throw new ConflictException("Un jefe debe confirmar los márgenes antes de enviar");
        }
        recalcular(c);
        // Un reenvío tras un rechazo conserva el número y suma una revisión.
        if (c.getNumero() == null) {
            c.setNumero(String.format("COT-%d-%04d", Year.now().getValue(), cotizaciones.siguienteNumero()));
        }
        c.setRevision(c.getRevision() + 1);
        c.setEstado(EstadoCotizacion.ENVIADA);
        c.setFechaEnvio(OffsetDateTime.now());
        c.setFechaRespuesta(null);
        if (c.getLead().getEstado() == EstadoLead.NUEVO) {
            c.getLead().setEstado(EstadoLead.COTIZADO);
        }
        cotizaciones.saveAndFlush(c);

        // Cada precio cotizado alimenta el historial de referencia (RF-CAT-04).
        for (CotizacionItem i : c.getItems()) {
            historial.save(HistorialPrecio.builder()
                    .producto(i.getProducto())
                    .proveedor(i.getProveedor())
                    .precioNeto(i.getPrecioNeto())
                    .disponible(true)
                    .registradoPor(usuario)
                    .fecha(LocalDateTime.now())
                    .cotizacionItemId(i.getId())
                    .build());
        }
        // El PDF enviado se guarda una vez: el cliente siempre ve el mismo documento.
        archivos.guardarGenerado(pdf.generar(c), nombrePdf(c), "application/pdf",
                ENTIDAD_PDF, c.getId(), CATEGORIA_PDF, usuario);
        return respuesta(c);
    }

    /**
     * Borra un borrador que nunca se envió (RF-COT-19). Uno que ya se envió y volvió a borrador tras
     * un rechazo no se borra: el cliente lo recibió y su PDF e historial de precios se conservan.
     * El informe técnico, si tenía, queda libre para cotizarse de nuevo.
     */
    @Transactional
    public void eliminar(UUID id, Long version) {
        Cotizacion c = buscar(id);
        if (version != null && !version.equals(c.getVersion())) {
            throw new ConflictException("Alguien más modificó la cotización. Recarga y vuelve a intentar.");
        }
        if (c.getEstado() != EstadoCotizacion.BORRADOR || c.getFechaEnvio() != null) {
            throw new ConflictException("Solo se elimina una cotización en borrador que nunca se envió al cliente");
        }
        cotizaciones.delete(c);
    }

    @Transactional
    public CotizacionResponse rechazar(UUID id, RechazoRequest req) {
        Cotizacion c = buscarEnviada(id, req.version());
        c.setEstado(EstadoCotizacion.RECHAZADA);
        c.setFechaRespuesta(OffsetDateTime.now());
        c.setMotivoRechazo(req.motivo().trim());
        return respuesta(cotizaciones.saveAndFlush(c));
    }

    /** El cliente acepta. No crea el proyecto: eso es una decisión aparte de Hauset (RN-11). */
    @Transactional
    public CotizacionResponse aceptar(UUID id, AccionRequest req, Usuario usuario) {
        Cotizacion c = buscarEnviada(id, req.version());
        c.setEstado(EstadoCotizacion.ACEPTADA);
        c.setFechaRespuesta(OffsetDateTime.now());
        c = cotizaciones.saveAndFlush(c);
        // Aviso a los jefes y a la secretaria: una tarea pública en su lista (RF-COT-18, RF-TAR-05).
        tareasEquipo.alAceptarCotizacion(c, usuario);
        return respuesta(c);
    }

    /**
     * Inicia el proyecto de una cotización aceptada, con instrucciones versión 1 y su checklist
     * (RF-COT-09, RF-PRY-02). Exige el informe técnico: sin él los instaladores no saben dónde va cada equipo.
     */
    @Transactional
    public CotizacionResponse iniciarProyecto(UUID id, AccionRequest req, Usuario usuario) {
        Cotizacion c = buscarConVersion(id, req.version());
        if (c.getEstado() != EstadoCotizacion.ACEPTADA) {
            throw new ConflictException("Solo una cotización aceptada puede iniciarse como proyecto");
        }
        if (c.getInforme() == null) {
            throw new ConflictException("Falta el informe técnico: el jefe de sistemas debe levantarlo desde la cotización");
        }
        if (proyectos.findByCotizacionId(c.getId()).isPresent()) {
            throw new ConflictException("La cotización ya tiene un proyecto");
        }
        cotizaciones.saveAndFlush(c);

        Proyecto proyecto = new Proyecto();
        proyecto.setCotizacion(c);
        proyecto.setEstado(EstadoProyecto.COTIZADO);
        proyecto = proyectos.save(proyecto);

        InstruccionVersion v1 = new InstruccionVersion();
        v1.setProyecto(proyecto);
        v1.setNumeroVersion(1);
        v1.setGeneradaDesde("COTIZACION");
        v1 = instrucciones.save(v1);

        int orden = 0;
        for (CotizacionItem i : c.getItems()) {
            InstruccionItem item = new InstruccionItem();
            item.setInstruccionVersion(v1);
            item.setOrden(orden++);
            item.setProducto(i.getProducto());
            item.setCantidad(i.getCantidad());
            item.setDescripcion(recortar("Instalar " + i.getCantidad() + " × " + i.getProductoNombre()));
            instruccionItems.save(item);
        }
        crearTareas(v1, c);
        tareasEquipo.alIniciarProyecto(c, usuario);
        eventosProyecto.save(new com.appdomotica.proyectos.entity.ProyectoEvento(proyecto, "PROYECTO_CREADO", usuario,
                "Iniciado desde la cotización " + c.getNumero() + " (informe v." + c.getInforme().getNumeroVersion() + ")"));
        return CotizacionResponse.de(c, proyecto.getId());
    }

    /**
     * Una tarea por producto y ubicación del informe técnico, para que el instalador sepa dónde va cada
     * equipo. Un producto que reemplaza a otro va donde iba el original. Lo cotizado de más (o que no está
     * en el informe) queda como tarea general.
     *
     * Una ubicación con checklist propia no genera tareas por producto: sus puntos se copian al proyecto
     * (la copia no cambia aunque después se cree otra versión del informe). Sus productos igual se descuentan
     * de lo cotizado para que no reaparezcan como tareas generales.
     */
    private void crearTareas(InstruccionVersion v1, Cotizacion c) {
        List<InformeTecnicoUbicacion> ubicaciones = c.getInforme() != null ? c.getInforme().getUbicaciones() : List.of();
        int orden = 0;
        // Primero por ubicación, en el orden del informe.
        Map<UUID, Integer> pendiente = new LinkedHashMap<>();
        Map<UUID, CotizacionItem> porProducto = new LinkedHashMap<>();
        Map<UUID, UUID> reemplazo = new HashMap<>();
        for (CotizacionItem i : c.getItems()) {
            pendiente.merge(i.getProducto().getId(), i.getCantidad(), Integer::sum);
            porProducto.putIfAbsent(i.getProducto().getId(), i);
            if (i.getProductoOriginal() != null) {
                reemplazo.putIfAbsent(i.getProductoOriginal().getId(), i.getProducto().getId());
            }
        }
        for (InformeTecnicoUbicacion u : ubicaciones) {
            boolean conChecklist = !u.getChecklist().isEmpty();
            if (conChecklist) {
                copiarChecklist(v1.getProyecto(), u);
            }
            for (InformeTecnicoItem it : u.getItems()) {
                UUID pedido = it.getProducto().getId();
                UUID prod = pendiente.getOrDefault(pedido, 0) > 0 ? pedido : reemplazo.getOrDefault(pedido, pedido);
                int quedan = pendiente.getOrDefault(prod, 0);
                int n = Math.min(quedan, it.getCantidad());
                if (n <= 0) {
                    continue;
                }
                pendiente.put(prod, quedan - n);
                if (!conChecklist) {
                    tareas.save(tarea(v1, porProducto.get(prod), n, u, orden++));
                }
            }
        }
        for (var e : pendiente.entrySet()) {
            if (e.getValue() > 0) {
                tareas.save(tarea(v1, porProducto.get(e.getKey()), e.getValue(), null, orden++));
            }
        }
    }

    private void copiarChecklist(Proyecto proyecto, InformeTecnicoUbicacion u) {
        for (var punto : u.getChecklist()) {
            ProyectoChecklistItem item = new ProyectoChecklistItem();
            item.setProyecto(proyecto);
            item.setUbicacion(u);
            item.setOrigenItemId(punto.getId());
            item.setTexto(punto.getTexto());
            item.setOrden(punto.getOrden());
            checklistProyecto.save(item);
        }
    }

    private static ChecklistTarea tarea(InstruccionVersion v, CotizacionItem item, int cantidad,
                                        InformeTecnicoUbicacion ubicacion, int orden) {
        ChecklistTarea t = new ChecklistTarea();
        t.setInstruccionVersion(v);
        t.setDescripcion(recortar("Instalar " + cantidad + " × " + item.getProductoNombre()));
        t.setProducto(item.getProducto());
        t.setCantidad(cantidad);
        t.setUbicacion(ubicacion);
        t.setOrden(orden);
        return t;
    }

    // ---------- Informe levantado después ----------

    /**
     * Liga a una cotización sin informe el que el jefe de sistemas levantó para ella (RF-COT-16). Precios,
     * márgenes e ítems no cambian (RN-10). Lo llama el servicio de informes dentro de su transacción.
     */
    @Transactional
    public Cotizacion vincularInforme(UUID cotizacionId, InformeTecnico informe) {
        Cotizacion c = buscar(cotizacionId);
        if (c.getInforme() != null) {
            throw new ConflictException("La cotización ya tiene informe técnico; para corregirlo crea una versión nueva");
        }
        if (!c.getLead().getId().equals(informe.getLead().getId())) {
            throw new BadRequestException("El informe y la cotización son de leads distintos");
        }
        c.setInforme(informe);
        c.setUpdatedAt(OffsetDateTime.now());
        return cotizaciones.saveAndFlush(c);
    }

    // ---------- PDF ----------

    /** PDF guardado si ya se envió; si es borrador, una vista previa con marca de agua. */
    @Transactional(readOnly = true)
    public PdfCotizacion pdf(UUID id) {
        Cotizacion c = buscar(id);
        if (c.getEstado() != EstadoCotizacion.BORRADOR) {
            Optional<ArchivoAdjunto> guardado = archivos.ultimo(ENTIDAD_PDF, c.getId(), CATEGORIA_PDF);
            if (guardado.isPresent()) {
                try (var in = archivos.cargarContenido(guardado.get()).getInputStream()) {
                    return new PdfCotizacion(nombrePdf(c), in.readAllBytes());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("No se pudo leer el PDF guardado", e);
                }
            }
        }
        String nombre = c.getNumero() != null ? c.getNumero() + "-borrador.pdf" : "cotizacion-borrador.pdf";
        return new PdfCotizacion(nombre, pdf.generar(c));
    }

    public record PdfCotizacion(String nombre, byte[] contenido) {
    }

    // ---------- Auxiliares ----------

    /** Calcula ítems completos y totales con la regla de la calculadora; los incompletos quedan sin precio. */
    static void recalcular(Cotizacion c) {
        List<CalculadoraCotizacion.Linea> lineas = new ArrayList<>();
        for (CotizacionItem i : c.getItems()) {
            if (i.getPrecioNeto() != null && i.getMargenPorcentaje() != null) {
                var l = CalculadoraCotizacion.linea(i.getPrecioNeto(), i.getMargenPorcentaje(), i.getCantidad(),
                        c.getIvaPorcentaje());
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
        c.setTotalSinIva(t.totalSinIva());
        c.setMontoIva(t.montoIva());
        c.setTotalConIva(t.totalConIva());
    }

    /** RN-02: el proveedor depende de la categoría del producto. Un proveedor sin categorías no se restringe. */
    static void validarProveedor(Proveedor proveedor, ProductoCatalogo producto) {
        if (!proveedor.esActivo()) {
            throw new BadRequestException("El proveedor " + proveedor.getNombre() + " no está activo");
        }
        String categorias = proveedor.getCategoriasAsociadas();
        if (categorias == null || categorias.isBlank()) {
            return;
        }
        boolean atiende = Arrays.stream(categorias.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT).replace(' ', '_'))
                .anyMatch(s -> s.equals(producto.getCategoria().name()));
        if (!atiende) {
            throw new BadRequestException("El proveedor " + proveedor.getNombre()
                    + " no atiende la categoría del producto " + producto.getNombre());
        }
    }

    private Cotizacion buscar(UUID id) {
        return cotizaciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada: " + id));
    }

    /**
     * Borrador, o rechazada para corregirla y reenviarla. Al tocar una rechazada vuelve a borrador
     * (conserva su número y el motivo del último rechazo).
     */
    private Cotizacion buscarEditable(UUID id, Long version) {
        Cotizacion c = buscarConVersion(id, version);
        if (!CotizacionDtos.esEditable(c)) {
            throw new ConflictException("La cotización está " + c.getEstado().name().toLowerCase()
                    + " y no se puede modificar");
        }
        c.setEstado(EstadoCotizacion.BORRADOR);
        return c;
    }

    /** COT-2026-0003.pdf; desde la segunda revisión, COT-2026-0003-rev2.pdf. */
    private static String nombrePdf(Cotizacion c) {
        return c.getNumero() + (c.getRevision() > 1 ? "-rev" + c.getRevision() : "") + ".pdf";
    }

    private Cotizacion buscarEnviada(UUID id, Long version) {
        Cotizacion c = buscarConVersion(id, version);
        if (c.getEstado() != EstadoCotizacion.ENVIADA) {
            throw new ConflictException("Solo una cotización enviada puede aceptarse o rechazarse");
        }
        return c;
    }

    private Cotizacion buscarConVersion(UUID id, Long version) {
        Cotizacion c = buscar(id);
        if (version != null && !version.equals(c.getVersion())) {
            throw new ConflictException("Alguien más modificó la cotización. Recarga y vuelve a intentar.");
        }
        // Marca la cotización como modificada aunque solo cambien sus ítems: así la versión
        // sube siempre y una edición concurrente se detecta (HTTP 409).
        c.setUpdatedAt(OffsetDateTime.now());
        return c;
    }

    private CotizacionResponse respuesta(Cotizacion c) {
        UUID proyectoId = c.getEstado() == EstadoCotizacion.ACEPTADA
                ? proyectos.findByCotizacionId(c.getId()).map(Proyecto::getId).orElse(null)
                : null;
        return CotizacionResponse.de(c, proyectoId);
    }

    private static String recortar(String s) {
        return s.length() <= 300 ? s : s.substring(0, 297) + "...";
    }
}
