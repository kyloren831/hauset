package com.appdomotica.clientes.dto;

import com.appdomotica.clientes.entity.Cliente;
import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.entity.Lead;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** DTOs de clientes y leads (RF-COT-01). */
public final class ClienteDtos {
    private ClienteDtos() {
    }

    public record ClienteRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio") @Size(max = 200) String nombre,
            @Size(max = 30) String telefono,
            @Size(max = 300) String direccion,
            String notas) {
    }

    public record ClienteResponse(UUID id, String nombre, String telefono, String direccion, String notas) {
        public static ClienteResponse de(Cliente c) {
            return new ClienteResponse(c.getId(), c.getNombre(), c.getTelefono(), c.getDireccion(), c.getNotas());
        }
    }

    /** Un lead nuevo: con un cliente existente (`clienteId`) o creando uno (`cliente`). */
    public record LeadRequest(
            UUID clienteId,
            @Valid ClienteRequest cliente,
            @Size(max = 40) String origen,
            String notas) {
    }

    public record CambioEstadoLeadRequest(@NotNull EstadoLead estado) {
    }

    public record LeadResponse(UUID id, ClienteResponse cliente, String origen, OffsetDateTime fecha,
                               EstadoLead estado, String notas) {
        public static LeadResponse de(Lead l) {
            return new LeadResponse(l.getId(), ClienteResponse.de(l.getCliente()), l.getOrigen(), l.getFecha(),
                    l.getEstado(), l.getNotas());
        }
    }

    /** Estado de los leads y tasa de conversión para marketing. */
    public record ResumenLeads(long nuevos, long cotizados, long descartados, long total, double tasaConversion) {
    }
}
