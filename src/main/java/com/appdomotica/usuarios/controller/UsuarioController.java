package com.appdomotica.usuarios.controller;

import com.appdomotica.usuarios.dto.UsuarioCreateRequest;
import com.appdomotica.usuarios.dto.UsuarioResponse;
import com.appdomotica.usuarios.dto.UsuarioUpdateRequest;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios/admin")
@PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarUsuarios(
            @RequestParam(required = false) RolUsuario rol,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(rol, activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crearUsuario(@Valid @RequestBody UsuarioCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crearUsuario(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<UsuarioResponse> cambiarEstado(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        Boolean activo = body.getOrDefault("activo", true);
        return ResponseEntity.ok(usuarioService.cambiarEstado(id, activo));
    }
}
