package com.appdomotica.mantenimiento.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.*;
import com.appdomotica.mantenimiento.service.EquiposService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Equipos instalados y credenciales (RF-MAN-01..06).
 * Consultar y ver contraseñas: todo el equipo menos marketing (RF-MAN-05). Registrar y editar: quienes
 * instalan (jefes, ingeniero industrial, instaladores). La bitácora de consultas: solo los jefes.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EquiposController {
    static final String EQUIPO = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR', 'SECRETARIA')";
    static final String CAMPO = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR')";
    static final String JEFES = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')";

    private final EquiposService service;

    @GetMapping("/equipos")
    @PreAuthorize(EQUIPO)
    public List<ProyectoConEquipos> buscar(@RequestParam(required = false) String buscar) {
        return service.buscar(buscar);
    }

    @GetMapping("/proyectos/{id}/equipos")
    @PreAuthorize(EQUIPO)
    public EquiposProyecto deProyecto(@PathVariable UUID id) {
        return service.deProyecto(id);
    }

    @PostMapping("/proyectos/{id}/equipos")
    @PreAuthorize(CAMPO)
    @ResponseStatus(HttpStatus.CREATED)
    public EquiposProyecto registrar(@PathVariable UUID id, @Valid @RequestBody EquipoRequest req,
                                     @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.registrar(id, req, usuario.getUsuario());
    }

    @PutMapping("/equipos/{id}")
    @PreAuthorize(CAMPO)
    public EquiposProyecto editar(@PathVariable UUID id, @Valid @RequestBody EquipoRequest req) {
        return service.editar(id, req);
    }

    @DeleteMapping("/equipos/{id}")
    @PreAuthorize(CAMPO)
    public EquiposProyecto retirar(@PathVariable UUID id, @RequestParam Long version,
                                   @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.retirar(id, version, usuario.getUsuario());
    }

    @PostMapping("/equipos/{id}/credenciales")
    @PreAuthorize(CAMPO)
    @ResponseStatus(HttpStatus.CREATED)
    public EquiposProyecto agregarCredencial(@PathVariable UUID id, @Valid @RequestBody CredencialRequest req,
                                             @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.agregarCredencial(id, req, usuario.getUsuario());
    }

    @PutMapping("/credenciales/{id}")
    @PreAuthorize(CAMPO)
    public EquiposProyecto editarCredencial(@PathVariable UUID id, @Valid @RequestBody CredencialRequest req,
                                            @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.editarCredencial(id, req, usuario.getUsuario());
    }

    @DeleteMapping("/credenciales/{id}")
    @PreAuthorize(CAMPO)
    public EquiposProyecto retirarCredencial(@PathVariable UUID id, @RequestParam Long version,
                                             @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.retirarCredencial(id, version, usuario.getUsuario());
    }

    /** Devuelve la contraseña y deja registro de quién la vio (RF-MAN-06). POST: no se guarda en cachés. */
    @PostMapping("/credenciales/{id}/revelar")
    @PreAuthorize(EQUIPO)
    public CredencialRevelada revelar(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.revelar(id, usuario.getUsuario());
    }

    @GetMapping("/credenciales/{id}/accesos")
    @PreAuthorize(JEFES)
    public List<Acceso> accesos(@PathVariable UUID id) {
        return service.accesos(id);
    }
}
