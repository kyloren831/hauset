package com.appdomotica.agenda.controller;

import com.appdomotica.agenda.dto.AgendaDtos.Agenda;
import com.appdomotica.agenda.service.AgendaService;
import com.appdomotica.config.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Agenda compartida (RF-AGE-01, RF-AGE-05). Solo consulta: agendar, mover y cancelar siguen en
 * `/api/proyectos/...` (jefes e ingeniero industrial, RF-AGE-02). Marketing no tiene acceso.
 */
@RestController
@RequestMapping("/api/agenda")
@RequiredArgsConstructor
public class AgendaController {
    private final AgendaService service;

    /** `desde`/`hasta`: días (AAAA-MM-DD, ambos incluidos; por omisión, hoy y 6 días más). `personaId`: filtro. */
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR', 'SECRETARIA')")
    public Agenda agenda(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                         @RequestParam(required = false) UUID personaId,
                         @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.agenda(usuario.getUsuario(), desde, hasta, personaId);
    }
}
