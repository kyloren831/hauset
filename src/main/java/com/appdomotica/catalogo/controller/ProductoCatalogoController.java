package com.appdomotica.catalogo.controller;

import com.appdomotica.catalogo.dto.CambioEstadoRequest;
import com.appdomotica.catalogo.dto.FiltroEstado;
import com.appdomotica.catalogo.dto.ProductoRequest;
import com.appdomotica.catalogo.dto.ProductoResponse;
import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.service.ProductoCatalogoService;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.config.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo de productos. Consultan todos menos marketing; crean y editan los jefes y el ingeniero
 * industrial; archivan y restauran solo los jefes.
 */
@RestController
@RequestMapping("/api/catalogo/productos")
@RequiredArgsConstructor
public class ProductoCatalogoController {
    private static final String EDITORES = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL')";
    private static final String JEFES = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')";
    private static final String LECTORES = "isAuthenticated() and !hasRole('MARKETING')";

    private final ProductoCatalogoService service;

    /** `estado`: ACTIVO (por defecto), INACTIVO, ARCHIVADO o TODOS. */
    @GetMapping
    @PreAuthorize(LECTORES)
    public PageResponse<ProductoResponse> listarProductos(
            @RequestParam(required = false) CategoriaProducto categoria,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ACTIVO") FiltroEstado estado,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.listarProductos(categoria, search, estado, pageable);
    }

    @GetMapping("/todos")
    @PreAuthorize(LECTORES)
    public List<ProductoResponse> listarTodosActivos() {
        return service.listarTodosActivos();
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTORES)
    public ProductoResponse obtenerPorId(@PathVariable UUID id) {
        return service.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDITORES)
    public ProductoResponse crearProducto(@Valid @RequestBody ProductoRequest request) {
        return service.crearProducto(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDITORES)
    public ProductoResponse actualizarProducto(@PathVariable UUID id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizarProducto(id, request);
    }

    /** Activar o desactivar. */
    @PutMapping("/{id}/estado")
    @PreAuthorize(EDITORES)
    public ProductoResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambioEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @PostMapping("/{id}/archivar")
    @PreAuthorize(JEFES)
    public ProductoResponse archivar(@PathVariable UUID id) {
        return service.archivar(id);
    }

    @PostMapping("/{id}/restaurar")
    @PreAuthorize(JEFES)
    public ProductoResponse restaurar(@PathVariable UUID id) {
        return service.restaurar(id);
    }

    /** Antes era una baja; ahora archiva (no se borra nada). */
    @DeleteMapping("/{id}")
    @PreAuthorize(JEFES)
    public ProductoResponse eliminarProducto(@PathVariable UUID id) {
        return service.archivar(id);
    }

    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(EDITORES)
    public ProductoResponse subirFoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.subirFoto(id, file, usuario.getUsuario());
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    @PreAuthorize(EDITORES)
    public ProductoResponse eliminarFoto(@PathVariable UUID id, @PathVariable UUID fotoId) {
        return service.eliminarFoto(id, fotoId);
    }
}
