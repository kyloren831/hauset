package com.appdomotica.clientes.service;

import com.appdomotica.clientes.dto.ClienteDtos.*;
import com.appdomotica.clientes.entity.Cliente;
import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.clientes.repository.ClienteRepository;
import com.appdomotica.clientes.repository.LeadRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Clientes y leads (RF-COT-01). */
@Service
@RequiredArgsConstructor
public class ClienteService {
    private final ClienteRepository clientes;
    private final LeadRepository leads;

    @Transactional(readOnly = true)
    public PageResponse<ClienteResponse> listarClientes(String busqueda, Pageable pageable) {
        var page = (busqueda == null || busqueda.isBlank())
                ? clientes.findByActivoTrue(pageable)
                : clientes.findByActivoTrueAndNombreContainingIgnoreCase(busqueda.trim(), pageable);
        return PageResponse.from(page.map(ClienteResponse::de));
    }

    @Transactional
    public ClienteResponse crearCliente(ClienteRequest req) {
        return ClienteResponse.de(clientes.save(aplicar(new Cliente(), req)));
    }

    @Transactional
    public ClienteResponse actualizarCliente(UUID id, ClienteRequest req) {
        return ClienteResponse.de(aplicar(buscarCliente(id), req));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> listarLeads(EstadoLead estado, Pageable pageable) {
        var page = estado == null ? leads.findAll(pageable) : leads.findByEstado(estado, pageable);
        return PageResponse.from(page.map(LeadResponse::de));
    }

    @Transactional
    public LeadResponse crearLead(LeadRequest req) {
        Cliente cliente;
        if (req.clienteId() != null) {
            cliente = buscarCliente(req.clienteId());
        } else if (req.cliente() != null) {
            cliente = clientes.save(aplicar(new Cliente(), req.cliente()));
        } else {
            throw new BadRequestException("Indica un cliente existente o los datos de uno nuevo");
        }
        Lead lead = new Lead();
        lead.setCliente(cliente);
        if (req.origen() != null && !req.origen().isBlank()) {
            lead.setOrigen(req.origen().trim().toUpperCase());
        }
        lead.setNotas(req.notas());
        return LeadResponse.de(leads.save(lead));
    }

    @Transactional
    public LeadResponse cambiarEstadoLead(UUID id, EstadoLead estado) {
        Lead lead = leads.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado: " + id));
        lead.setEstado(estado);
        return LeadResponse.de(lead);
    }

    @Transactional(readOnly = true)
    public ResumenLeads resumen() {
        long nuevos = leads.countByEstado(EstadoLead.NUEVO);
        long cotizados = leads.countByEstado(EstadoLead.COTIZADO);
        long descartados = leads.countByEstado(EstadoLead.DESCARTADO);
        long total = nuevos + cotizados + descartados;
        double tasa = total == 0 ? 0 : Math.round(cotizados * 1000.0 / total) / 10.0;
        return new ResumenLeads(nuevos, cotizados, descartados, total, tasa);
    }

    private Cliente buscarCliente(UUID id) {
        return clientes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + id));
    }

    private static Cliente aplicar(Cliente c, ClienteRequest req) {
        c.setNombre(req.nombre().trim());
        c.setTelefono(req.telefono() == null ? null : req.telefono().replaceAll("[^0-9+]", ""));
        c.setDireccion(req.direccion());
        c.setNotas(req.notas());
        return c;
    }
}
