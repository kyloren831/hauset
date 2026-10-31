package com.appdomotica.catalogo.service;

import com.appdomotica.catalogo.dto.FiltroEstado;
import com.appdomotica.catalogo.dto.ProductoRequest;
import com.appdomotica.catalogo.dto.ProductoResponse;
import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Productos del catálogo (RF-CAT-01), sin precio fijo (RN-01), con fotos y estados. */
@Service
@RequiredArgsConstructor
public class ProductoCatalogoService {
    public static final String ENTIDAD_PRODUCTO = "PRODUCTO_CATALOGO";
    public static final String CATEGORIA_FOTO = "FOTO_PRODUCTO";
    public static final int MAX_FOTOS = 10;

    private final ProductoCatalogoRepository productoCatalogoRepository;
    private final ArchivoAdjuntoRepository archivosRepo;
    private final ArchivoAdjuntoService archivos;

    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> listarProductos(
            CategoriaProducto categoria, String search, FiltroEstado estado, Pageable pageable) {
        var filtro = estado == null ? FiltroEstado.ACTIVO : estado;
        var categorias = categoria == null ? EnumSet.allOf(CategoriaProducto.class) : EnumSet.of(categoria);
        var page = productoCatalogoRepository.buscar(filtro.estados(), categorias, FiltroEstado.patron(search), pageable);
        Map<UUID, List<UUID>> fotos = fotosPorProducto(page.getContent().stream().map(ProductoCatalogo::getId).toList());
        return PageResponse.from(page.map(p -> ProductoResponse.fromEntity(p, fotos.getOrDefault(p.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarTodosActivos() {
        var lista = productoCatalogoRepository.findByEstadoOrderByNombreAsc(EstadoCatalogo.ACTIVO);
        Map<UUID, List<UUID>> fotos = fotosPorProducto(lista.stream().map(ProductoCatalogo::getId).toList());
        return lista.stream()
                .map(p -> ProductoResponse.fromEntity(p, fotos.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Se puede consultar en cualquier estado. */
    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorId(UUID id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public ProductoResponse crearProducto(ProductoRequest request) {
        String nombre = request.getNombre().trim();
        if (productoCatalogoRepository.nombreEnUso(nombre, null)) {
            throw new ConflictException("Ya existe un producto con el nombre " + nombre
                    + ". Si está inactivo, actívalo en lugar de crear otro.");
        }
        ProductoCatalogo producto = new ProductoCatalogo();
        aplicar(producto, request);
        producto.setEstado(EstadoCatalogo.ACTIVO);
        return respuesta(productoCatalogoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizarProducto(UUID id, ProductoRequest request) {
        ProductoCatalogo producto = buscarNoArchivado(id);
        if (productoCatalogoRepository.nombreEnUso(request.getNombre().trim(), id)) {
            throw new ConflictException("Ya existe otro producto con el nombre " + request.getNombre().trim());
        }
        aplicar(producto, request);
        return respuesta(productoCatalogoRepository.save(producto));
    }

    /** Activar o desactivar. Un archivado primero se restaura. */
    @Transactional
    public ProductoResponse cambiarEstado(UUID id, EstadoCatalogo nuevo) {
        if (nuevo == EstadoCatalogo.ARCHIVADO) {
            throw new BadRequestException("Para archivar usa la opción Archivar");
        }
        ProductoCatalogo producto = buscarNoArchivado(id);
        producto.setEstado(nuevo);
        return respuesta(productoCatalogoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse archivar(UUID id) {
        ProductoCatalogo producto = buscar(id);
        producto.setEstado(EstadoCatalogo.ARCHIVADO);
        return respuesta(productoCatalogoRepository.save(producto));
    }

    /** Vuelve como activo, si su nombre no lo tomó otro producto mientras estaba archivado. */
    @Transactional
    public ProductoResponse restaurar(UUID id) {
        ProductoCatalogo producto = buscar(id);
        if (producto.getEstado() != EstadoCatalogo.ARCHIVADO) {
            return respuesta(producto);
        }
        if (productoCatalogoRepository.nombreEnUso(producto.getNombre(), id)) {
            throw new ConflictException("Ya existe otro producto con el nombre " + producto.getNombre()
                    + "; cambia uno de los dos antes de restaurar");
        }
        producto.setEstado(EstadoCatalogo.ACTIVO);
        return respuesta(productoCatalogoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse subirFoto(UUID id, MultipartFile foto, Usuario usuario) {
        ProductoCatalogo producto = buscarNoArchivado(id);
        ValidadorImagen.validar(foto);
        if (fotosDe(id).size() >= MAX_FOTOS) {
            throw new BadRequestException("Un producto puede tener como mucho " + MAX_FOTOS + " fotos");
        }
        archivos.subirArchivo(foto, ENTIDAD_PRODUCTO, id, CATEGORIA_FOTO, producto.getNombre(), usuario);
        return respuesta(producto);
    }

    @Transactional
    public ProductoResponse eliminarFoto(UUID id, UUID fotoId) {
        ProductoCatalogo producto = buscarNoArchivado(id);
        if (fotosDe(id).stream().noneMatch(a -> a.getId().equals(fotoId))) {
            throw new ResourceNotFoundException("La foto no pertenece a este producto");
        }
        archivos.eliminarArchivo(fotoId);
        return respuesta(producto);
    }

    private ProductoResponse respuesta(ProductoCatalogo p) {
        return ProductoResponse.fromEntity(p, fotosDe(p.getId()).stream().map(ArchivoAdjunto::getId).toList());
    }

    /** Fotos vigentes, de la más antigua a la más nueva (la primera es la principal). */
    private List<ArchivoAdjunto> fotosDe(UUID productoId) {
        return archivosRepo.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(
                        ENTIDAD_PRODUCTO, productoId, CATEGORIA_FOTO).stream()
                .sorted(Comparator.comparing(ArchivoAdjunto::getCreatedAt))
                .toList();
    }

    private Map<UUID, List<UUID>> fotosPorProducto(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return archivosRepo.findByEntidadTipoAndEntidadIdInAndCategoriaAndDeletedAtIsNull(
                        ENTIDAD_PRODUCTO, ids, CATEGORIA_FOTO).stream()
                .sorted(Comparator.comparing(ArchivoAdjunto::getCreatedAt))
                .collect(Collectors.groupingBy(ArchivoAdjunto::getEntidadId,
                        Collectors.mapping(ArchivoAdjunto::getId, Collectors.toList())));
    }

    private void aplicar(ProductoCatalogo producto, ProductoRequest request) {
        producto.setNombre(request.getNombre().trim());
        producto.setCategoria(request.getCategoria());
        producto.setEspecificacion(request.getEspecificacion() == null || request.getEspecificacion().isBlank()
                ? null : request.getEspecificacion().trim());
    }

    private ProductoCatalogo buscarNoArchivado(UUID id) {
        ProductoCatalogo producto = buscar(id);
        if (producto.getEstado() == EstadoCatalogo.ARCHIVADO) {
            throw new ConflictException("El producto está archivado; restáuralo antes de modificarlo");
        }
        return producto;
    }

    private ProductoCatalogo buscar(UUID id) {
        return productoCatalogoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado en catálogo con ID: " + id));
    }
}
