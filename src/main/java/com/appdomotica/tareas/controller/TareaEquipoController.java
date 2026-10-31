package com.appdomotica.tareas.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.tareas.dto.TareaEquipoDtos.*;
import com.appdomotica.tareas.service.TareaEquipoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Lista de tareas de la oficina: solo los dos jefes y la secretaria (RF-TAR-07). */
@RestController
@RequestMapping("/api/tareas-equipo")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA')")
public class TareaEquipoController {

    private final TareaEquipoService service;

    /** Públicas y mis privadas; de las hechas, las de los últimos 30 días. */
    @GetMapping
    public List<TareaResponse> listar(@AuthenticationPrincipal CustomUserDetails yo) {
        return service.listar(yo.getUsuario());
    }

    @GetMapping("/personas")
    public List<PersonaResponse> personas() {
        return service.personas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TareaResponse crear(@Valid @RequestBody NuevaTareaRequest req, @AuthenticationPrincipal CustomUserDetails yo) {
        return service.crear(req, yo.getUsuario());
    }

    @PutMapping("/{id}")
    public TareaResponse editar(@PathVariable UUID id, @Valid @RequestBody EditarTareaRequest req,
                                @AuthenticationPrincipal CustomUserDetails yo) {
        return service.editar(id, req, yo.getUsuario());
    }

    @PostMapping("/{id}/mover")
    public TareaResponse mover(@PathVariable UUID id, @Valid @RequestBody MoverTareaRequest req,
                               @AuthenticationPrincipal CustomUserDetails yo) {
        return service.mover(id, req, yo.getUsuario());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @RequestParam(required = false) Long version,
                         @AuthenticationPrincipal CustomUserDetails yo) {
        service.eliminar(id, version, yo.getUsuario());
    }
}
