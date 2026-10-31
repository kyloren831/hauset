package com.appdomotica.clientes.controller;

import com.appdomotica.clientes.dto.ClienteDtos.*;
import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.service.ClienteService;
import com.appdomotica.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClienteController {
    private static final String COMERCIAL = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA')";

    private final ClienteService service;

    @GetMapping("/clientes")
    @PreAuthorize(COMERCIAL)
    public PageResponse<ClienteResponse> listarClientes(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return service.listarClientes(search, pageable);
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(COMERCIAL)
    public ClienteResponse crearCliente(@Valid @RequestBody ClienteRequest req) {
        return service.crearCliente(req);
    }

    @PutMapping("/clientes/{id}")
    @PreAuthorize(COMERCIAL)
    public ClienteResponse actualizarCliente(@PathVariable UUID id, @Valid @RequestBody ClienteRequest req) {
        return service.actualizarCliente(id, req);
    }

    /** Marketing consulta el estado de los leads (no los modifica). */
    @GetMapping("/leads")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA', 'MARKETING')")
    public PageResponse<LeadResponse> listarLeads(
            @RequestParam(required = false) EstadoLead estado,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listarLeads(estado, pageable);
    }

    @GetMapping("/leads/resumen")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA', 'MARKETING')")
    public ResumenLeads resumen() {
        return service.resumen();
    }

    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(COMERCIAL)
    public LeadResponse crearLead(@Valid @RequestBody LeadRequest req) {
        return service.crearLead(req);
    }

    @PatchMapping("/leads/{id}/estado")
    @PreAuthorize(COMERCIAL)
    public LeadResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambioEstadoLeadRequest req) {
        return service.cambiarEstadoLead(id, req.estado());
    }
}
