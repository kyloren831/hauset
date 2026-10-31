package com.appdomotica.cotizaciones.service;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.repository.LeadRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.InformeRequest;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.InformeResponse;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.ItemInformeRequest;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.UbicacionRequest;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.VersionInformeRequest;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.VersionResumen;
import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.entity.InformeTecnico;
import com.appdomotica.cotizaciones.entity.InformeTecnicoItem;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import com.appdomotica.cotizaciones.repository.CotizacionRepository;
import com.appdomotica.cotizaciones.repository.InformeTecnicoRepository;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.ValidadorImagen;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Informe técnico por ubicaciones (foto marcada, nota y productos), sin precios (RF-COT-02).
 * No se modifica: se corrige creando una versión nueva (v.1, v.2, ...). Cada versión puede tener
 * una cotización.
 */
@Service
@RequiredArgsConstructor
public class InformeTecnicoService {
    private final InformeTecnicoRepository informes;
    private final LeadRepository leads;
    private final ProductoCatalogoRepository productos;
    private final CotizacionRepository cotizaciones;
    private final ArchivoAdjuntoRepository archivosRepo;
    private final ArchivoAdjuntoService archivos;
    private final CotizacionService cotizacionService;
    private final com.appdomotica.proyectos.repository.ProyectoRepository proyectos;

    /** Foto de cada ubicación, con las marcas ya dibujadas encima. */
    public static final String ENTIDAD_UBICACION = "INFORME_TECNICO_UBICACION";
    public static final String CATEGORIA_FOTO = "FOTO_UBICACION";

    /** Última versión de cada informe, con la lista de sus versiones. */
    @Transactional(readOnly = true)
    public PageResponse<InformeResponse> listar(Pageable pageable) {
        var page = informes.ultimasVersiones(pageable);
        Map<UUID, UUID> fotos = fotosPorUbicacion(page.getContent().stream()
                .flatMap(i -> i.getUbicaciones().stream()).map(InformeTecnicoUbicacion::getId).toList());
        Map<UUID, List<VersionResumen>> versiones = versionesPorGrupo(
                page.getContent().stream().map(InformeTecnico::getGrupoId).collect(Collectors.toSet()));
        return PageResponse.from(page.map(i -> InformeResponse.de(i, fotos, versiones.get(i.getGrupoId()), conProyecto(i))));
    }

    /** Cualquier versión. */
    @Transactional(readOnly = true)
    public InformeResponse obtener(UUID id) {
        return respuesta(buscar(id));
    }

    /** Versión 1 de un informe nuevo. Con `cotizacionId`, se levanta para esa cotización y queda ligado a ella. */
    @Transactional
    public InformeResponse crear(InformeRequest req, Usuario ingeniero) {
        var lead = leads.findById(req.leadId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado: " + req.leadId()));
        if (lead.getEstado() == EstadoLead.DESCARTADO) {
            throw new ConflictException("El lead está descartado");
        }
        InformeTecnico informe = new InformeTecnico();
        informe.setLead(lead);
        informe.setIngeniero(ingeniero);
        informe.setGrupoId(UUID.randomUUID());
        informe.setNumeroVersion(1);
        informe.setNotas(req.notas());
        informe.setDesdeCotizacion(req.cotizacionId() != null);
        agregarUbicaciones(informe, req.ubicaciones(), Set.of());
        informe = informes.saveAndFlush(informe);
        if (req.cotizacionId() != null) {
            cotizacionService.vincularInforme(req.cotizacionId(), informe);
        }
        return respuesta(informe);
    }

    /**
     * Crea la versión siguiente del mismo informe a partir de otra (cualquiera, no solo la última).
     * La de origen y su cotización no cambian. Las ubicaciones con `fotoDe` copian esa foto.
     */
    @Transactional
    public InformeResponse nuevaVersion(UUID desdeId, VersionInformeRequest req, Usuario ingeniero) {
        InformeTecnico origen = buscar(desdeId);
        Set<UUID> ubicacionesDelGrupo = informes.findByGrupoIdInOrderByNumeroVersionAsc(Set.of(origen.getGrupoId()))
                .stream().flatMap(i -> i.getUbicaciones().stream()).map(InformeTecnicoUbicacion::getId)
                .collect(Collectors.toSet());

        InformeTecnico nueva = new InformeTecnico();
        nueva.setLead(origen.getLead());
        nueva.setIngeniero(ingeniero);
        nueva.setGrupoId(origen.getGrupoId());
        nueva.setNumeroVersion(informes.ultimaVersion(origen.getGrupoId()) + 1);
        nueva.setNotas(req.notas());
        agregarUbicaciones(nueva, req.ubicaciones(), ubicacionesDelGrupo);
        nueva = informes.saveAndFlush(nueva);

        // Las ubicaciones guardadas quedan en el mismo orden que las pedidas.
        for (int i = 0; i < req.ubicaciones().size(); i++) {
            UUID fotoDe = req.ubicaciones().get(i).fotoDe();
            if (fotoDe != null) {
                copiarFoto(fotoDe, nueva.getUbicaciones().get(i).getId(), ingeniero);
            }
        }
        return respuesta(nueva);
    }

    /**
     * Guarda la foto (con las marcas ya dibujadas) de una ubicación. La anterior queda dada de baja.
     * Solo mientras la versión no tenga cotización; después, los cambios van en una versión nueva. Un informe
     * levantado para una cotización ya hecha admite fotos hasta que se inicie el proyecto.
     */
    @Transactional
    public InformeResponse subirFotoUbicacion(UUID informeId, UUID ubicacionId, MultipartFile foto, Usuario usuario) {
        InformeTecnico informe = buscarSinCotizacion(informeId);
        InformeTecnicoUbicacion ubicacion = ubicacionDe(informe, ubicacionId);
        ValidadorImagen.validar(foto);
        darDeBajaFotos(ubicacion.getId());
        archivos.subirArchivo(foto, ENTIDAD_UBICACION, ubicacion.getId(), CATEGORIA_FOTO,
                "Ubicación " + (ubicacion.getOrden() + 1) + " del informe v." + informe.getNumeroVersion(), usuario);
        return respuesta(informe);
    }

    @Transactional
    public InformeResponse eliminarFotoUbicacion(UUID informeId, UUID ubicacionId) {
        InformeTecnico informe = buscarSinCotizacion(informeId);
        darDeBajaFotos(ubicacionDe(informe, ubicacionId).getId());
        return respuesta(informe);
    }

    private InformeTecnico buscarSinCotizacion(UUID id) {
        InformeTecnico informe = buscar(id);
        Optional<Cotizacion> cot = cotizaciones.findByInformeId(id);
        if (cot.isPresent() && informe.isDesdeCotizacion()) {
            if (proyectos.findByCotizacionId(cot.get().getId()).isPresent()) {
                throw new ConflictException("El proyecto ya se inició; las fotos de este informe ya no se cambian");
            }
            return informe;
        }
        if (cot.isPresent()) {
            throw new ConflictException("Esta versión ya tiene cotización; para cambiarla crea una versión nueva");
        }
        return informe;
    }

    private InformeTecnicoUbicacion ubicacionDe(InformeTecnico informe, UUID ubicacionId) {
        return informe.getUbicaciones().stream()
                .filter(u -> u.getId().equals(ubicacionId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La ubicación no pertenece a este informe"));
    }

    private void darDeBajaFotos(UUID ubicacionId) {
        archivosRepo.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(ENTIDAD_UBICACION, ubicacionId, CATEGORIA_FOTO)
                .forEach(a -> archivos.eliminarArchivo(a.getId()));
    }

    /** Duplica el archivo (cada adjunto tiene su propio archivo en disco) para la ubicación nueva. */
    private void copiarFoto(UUID desdeUbicacion, UUID haciaUbicacion, Usuario usuario) {
        Optional<ArchivoAdjunto> foto = archivos.ultimo(ENTIDAD_UBICACION, desdeUbicacion, CATEGORIA_FOTO);
        if (foto.isEmpty()) {
            return;
        }
        try (var in = archivos.cargarContenido(foto.get()).getInputStream()) {
            archivos.guardarGenerado(in.readAllBytes(), foto.get().getNombreOriginal(), foto.get().getMimeType(),
                    ENTIDAD_UBICACION, haciaUbicacion, CATEGORIA_FOTO, usuario);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo copiar la foto de la versión anterior", e);
        }
    }

    private InformeResponse respuesta(InformeTecnico informe) {
        return InformeResponse.de(informe,
                fotosPorUbicacion(informe.getUbicaciones().stream().map(InformeTecnicoUbicacion::getId).toList()),
                versionesPorGrupo(Set.of(informe.getGrupoId())).get(informe.getGrupoId()), conProyecto(informe));
    }

    /** Solo importa para los informes levantados desde una cotización (fotos editables hasta iniciar). */
    private boolean conProyecto(InformeTecnico informe) {
        return informe.isDesdeCotizacion() && cotizaciones.findByInformeId(informe.getId())
                .flatMap(c -> proyectos.findByCotizacionId(c.getId())).isPresent();
    }

    /** Versiones de cada grupo con su cotización, de la v.1 en adelante. */
    private Map<UUID, List<VersionResumen>> versionesPorGrupo(Set<UUID> grupos) {
        if (grupos.isEmpty()) {
            return Map.of();
        }
        List<InformeTecnico> todas = informes.findByGrupoIdInOrderByNumeroVersionAsc(grupos);
        Map<UUID, Cotizacion> cotPorInforme = cotizaciones.findByInformeIdIn(
                        todas.stream().map(InformeTecnico::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getInforme().getId(), Function.identity()));
        Map<UUID, List<VersionResumen>> r = new HashMap<>();
        for (InformeTecnico i : todas) {
            Cotizacion c = cotPorInforme.get(i.getId());
            r.computeIfAbsent(i.getGrupoId(), g -> new ArrayList<>()).add(new VersionResumen(
                    i.getId(), i.getNumeroVersion(), i.getFecha(),
                    c != null ? c.getId() : null, c != null ? c.getNumero() : null,
                    c != null ? c.getEstado() : null, c != null ? c.getRevision() : null));
        }
        return r;
    }

    /** Última foto vigente de cada ubicación. */
    public Map<UUID, UUID> fotosPorUbicacion(List<UUID> ubicacionIds) {
        if (ubicacionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ArchivoAdjunto> ultima = new HashMap<>();
        for (ArchivoAdjunto a : archivosRepo.findByEntidadTipoAndEntidadIdInAndCategoriaAndDeletedAtIsNull(
                ENTIDAD_UBICACION, ubicacionIds, CATEGORIA_FOTO)) {
            ultima.merge(a.getEntidadId(), a, (x, y) -> x.getCreatedAt().isAfter(y.getCreatedAt()) ? x : y);
        }
        Map<UUID, UUID> r = new HashMap<>();
        ultima.forEach((ubicacion, archivo) -> r.put(ubicacion, archivo.getId()));
        return r;
    }

    /** Agrega las ubicaciones pedidas, en orden. `fotoDe` debe ser una ubicación del mismo informe. */
    private void agregarUbicaciones(InformeTecnico informe, List<UbicacionRequest> pedidas, Set<UUID> fotosPermitidas) {
        int orden = 0;
        for (UbicacionRequest r : pedidas) {
            if (r.fotoDe() != null && !fotosPermitidas.contains(r.fotoDe())) {
                throw new BadRequestException("La foto a copiar no es de este informe");
            }
            InformeTecnicoUbicacion u = new InformeTecnicoUbicacion();
            u.setInforme(informe);
            u.setNota(r.nota() == null || r.nota().isBlank() ? null : r.nota().trim());
            u.setOrden(orden++);
            agregarItems(informe, u, r.items());
            informe.getUbicaciones().add(u);
        }
    }

    /**
     * Productos de una ubicación. En una versión nueva se aceptan los productos que ya estaban en el
     * informe aunque hoy estén inactivos; los que se agregan por primera vez deben estar activos.
     */
    private void agregarItems(InformeTecnico informe, InformeTecnicoUbicacion u, List<ItemInformeRequest> pedidos) {
        var vistos = new HashSet<UUID>();
        int orden = 0;
        for (ItemInformeRequest p : pedidos) {
            if (!vistos.add(p.productoId())) {
                throw new BadRequestException("Un producto aparece dos veces en la misma ubicación; suma las cantidades");
            }
            ProductoCatalogo producto = productos.findById(p.productoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + p.productoId()));
            if (!producto.esActivo() && !yaEstabaEnElInforme(informe, producto.getId())) {
                throw new BadRequestException("El producto " + producto.getNombre() + " no está activo en el catálogo");
            }
            InformeTecnicoItem item = new InformeTecnicoItem();
            item.setInforme(informe);
            item.setUbicacion(u);
            item.setProducto(producto);
            item.setCantidad(p.cantidad());
            item.setOrden(orden++);
            u.getItems().add(item);
        }
    }

    private boolean yaEstabaEnElInforme(InformeTecnico informe, UUID productoId) {
        if (informe.getGrupoId() == null || informe.getNumeroVersion() == 1) {
            return false;
        }
        return informes.findByGrupoIdInOrderByNumeroVersionAsc(Set.of(informe.getGrupoId())).stream()
                .flatMap(i -> i.todosLosItems().stream())
                .anyMatch(it -> it.getProducto().getId().equals(productoId));
    }

    private InformeTecnico buscar(UUID id) {
        return informes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Informe no encontrado: " + id));
    }
}
