package com.appdomotica.catalogo.controller;

import com.appdomotica.catalogo.dto.CambioEstadoRequest;
import com.appdomotica.catalogo.dto.FiltroEstado;
import com.appdomotica.catalogo.dto.ProveedorRequest;
import com.appdomotica.catalogo.dto.ProveedorResponse;
import com.appdomotica.catalogo.service.ProveedorService;
import com.appdomotica.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Proveedores (RF-CAT-02). Consultan todos menos marketing; crean y editan jefes, secretaria e
 * ingeniero industrial; archivan y restauran solo los jefes.
 */
@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {
    private static final String EDITORES =
            "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA', 'INGENIERO_INDUSTRIAL')";
    private static final String JEFES = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')";
    private static final String LECTORES = "isAuthenticated() and !hasRole('MARKETING')";

    private final ProveedorService proveedorService;

    /** `estado`: ACTIVO (por defecto), INACTIVO, ARCHIVADO o TODOS. */
    @GetMapping
    @PreAuthorize(LECTORES)
    public PageResponse<ProveedorResponse> listarProveedores(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ACTIVO") FiltroEstado estado,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return proveedorService.listarProveedores(search, estado, pageable);
    }

    @GetMapping("/todos")
    @PreAuthorize(LECTORES)
    public List<ProveedorResponse> listarTodosActivos() {
        return proveedorService.listarTodosActivos();
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTORES)
    public ProveedorResponse obtenerPorId(@PathVariable UUID id) {
        return proveedorService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDITORES)
    public ProveedorResponse crearProveedor(@Valid @RequestBody ProveedorRequest request) {
        return proveedorService.crearProveedor(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDITORES)
    public ProveedorResponse actualizarProveedor(@PathVariable UUID id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizarProveedor(id, request);
    }

    /** Activar o desactivar. */
    @PutMapping("/{id}/estado")
    @PreAuthorize(EDITORES)
    public ProveedorResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambioEstadoRequest request) {
        return proveedorService.cambiarEstado(id, request.estado());
    }

    @PostMapping("/{id}/archivar")
    @PreAuthorize(JEFES)
    public ProveedorResponse archivar(@PathVariable UUID id) {
        return proveedorService.archivar(id);
    }

    @PostMapping("/{id}/restaurar")
    @PreAuthorize(JEFES)
    public ProveedorResponse restaurar(@PathVariable UUID id) {
        return proveedorService.restaurar(id);
    }

    /** Antes era una baja; ahora archiva (no se borra nada). */
    @DeleteMapping("/{id}")
    @PreAuthorize(JEFES)
    public ProveedorResponse eliminarProveedor(@PathVariable UUID id) {
        return proveedorService.archivar(id);
    }
}
