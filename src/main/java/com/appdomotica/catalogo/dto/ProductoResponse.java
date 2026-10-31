package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** `fotoIds`: fotos del producto, la primera es la principal; se descargan en /api/v1/archivos/{id}/descargar. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoResponse {
    private UUID id;
    private String nombre;
    private CategoriaProducto categoria;
    private String especificacion;
    private EstadoCatalogo estado;
    /** Igual a estado == ACTIVO; se mantiene para clientes anteriores. */
    private Boolean activo;
    @Builder.Default
    private List<UUID> fotoIds = List.of();

    public static ProductoResponse fromEntity(ProductoCatalogo producto) {
        return fromEntity(producto, List.of());
    }

    public static ProductoResponse fromEntity(ProductoCatalogo producto, List<UUID> fotoIds) {
        return ProductoResponse.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .categoria(producto.getCategoria())
                .especificacion(producto.getEspecificacion())
                .estado(producto.getEstado())
                .activo(producto.esActivo())
                .fotoIds(fotoIds)
                .build();
    }
}
