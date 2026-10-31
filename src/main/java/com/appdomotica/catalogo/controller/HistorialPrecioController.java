package com.appdomotica.catalogo.controller;

import com.appdomotica.catalogo.dto.HistorialPrecioResponse;
import com.appdomotica.catalogo.dto.RegistrarPrecioRequest;
import com.appdomotica.catalogo.service.HistorialPrecioService;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.config.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalogo/precios")
// Los precios netos de proveedor no los ven ni los instaladores ni marketing (RN-03).
@PreAuthorize("hasAnyRole('SECRETARIA', 'JEFE_ELECTROMECANICO', 'JEFE_SISTEMAS')")
public class HistorialPrecioController {

    @Autowired
    private HistorialPrecioService historialPrecioService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SECRETARIA', 'JEFE_ELECTROMECANICO', 'JEFE_SISTEMAS')")
    public ResponseEntity<HistorialPrecioResponse> registrarPrecio(
            @Valid @RequestBody RegistrarPrecioRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(historialPrecioService.registrarPrecio(request, userDetails));
    }

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<PageResponse<HistorialPrecioResponse>> listarPorProducto(
            @PathVariable UUID productoId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(historialPrecioService.listarHistorialPorProducto(productoId, pageable));
    }

    @GetMapping("/producto/{productoId}/ultimos")
    public ResponseEntity<List<HistorialPrecioResponse>> listarUltimosPreciosPorProducto(
            @PathVariable UUID productoId) {
        return ResponseEntity.ok(historialPrecioService.listarUltimosPreciosPorProducto(productoId));
    }

    @GetMapping("/proveedor/{proveedorId}")
    public ResponseEntity<PageResponse<HistorialPrecioResponse>> listarPorProveedor(
            @PathVariable UUID proveedorId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(historialPrecioService.listarHistorialPorProveedor(proveedorId, pageable));
    }
}
