package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorResponse {
    private UUID id;
    private String nombre;
    private String categoriasAsociadas;
    private String contacto;
    private EstadoCatalogo estado;
    /** Igual a estado == ACTIVO; se mantiene para clientes anteriores. */
    private Boolean activo;

    public static ProveedorResponse fromEntity(Proveedor proveedor) {
        return ProveedorResponse.builder()
                .id(proveedor.getId())
                .nombre(proveedor.getNombre())
                .categoriasAsociadas(proveedor.getCategoriasAsociadas())
                .contacto(proveedor.getContacto())
                .estado(proveedor.getEstado())
                .activo(proveedor.esActivo())
                .build();
    }
}
