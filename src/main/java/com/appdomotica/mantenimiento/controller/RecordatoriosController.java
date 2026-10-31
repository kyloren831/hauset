package com.appdomotica.mantenimiento.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.Recordatorio;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.Recordatorios;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.ResolverRecordatorioRequest;
import com.appdomotica.mantenimiento.entity.EstadoRecordatorio;
import com.appdomotica.mantenimiento.service.RecordatoriosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/** Recordatorios de mantenimiento (RF-MAN-07..09): jefes, ingeniero industrial y secretaria les dan seguimiento. */
@RestController
@RequestMapping("/api/mantenimiento/recordatorios")
@RequiredArgsConstructor
public class RecordatoriosController {
    static final String SEGUIMIENTO = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'SECRETARIA')";

    private final RecordatoriosService service;

    @GetMapping
    @PreAuthorize(SEGUIMIENTO)
    public Recordatorios listar() {
        return service.listar(LocalDate.now(RecordatoriosService.CR));
    }

    @PostMapping("/{id}/contactado")
    @PreAuthorize(SEGUIMIENTO)
    public Recordatorio contactado(@PathVariable UUID id, @Valid @RequestBody ResolverRecordatorioRequest req,
                                   @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.resolver(id, EstadoRecordatorio.CONTACTADO, req, usuario.getUsuario());
    }

    @PostMapping("/{id}/omitido")
    @PreAuthorize(SEGUIMIENTO)
    public Recordatorio omitido(@PathVariable UUID id, @Valid @RequestBody ResolverRecordatorioRequest req,
                                @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.resolver(id, EstadoRecordatorio.OMITIDO, req, usuario.getUsuario());
    }
}
