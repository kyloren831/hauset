package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.HistorialPrecio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialPrecioResponse {
    private UUID id;
    private UUID productoId;
    private String productoNombre;
    private UUID proveedorId;
    private String proveedorNombre;
    private BigDecimal precioNeto;
    private Boolean disponible;
    private UUID registradoPorId;
    private String registradoPorNombre;
    private String registradoPorRol;
    private LocalDateTime fecha;

    public static HistorialPrecioResponse fromEntity(HistorialPrecio historial) {
        return HistorialPrecioResponse.builder()
                .id(historial.getId())
                .productoId(historial.getProducto() != null ? historial.getProducto().getId() : null)
                .productoNombre(historial.getProducto() != null ? historial.getProducto().getNombre() : null)
                .proveedorId(historial.getProveedor() != null ? historial.getProveedor().getId() : null)
                .proveedorNombre(historial.getProveedor() != null ? historial.getProveedor().getNombre() : null)
                .precioNeto(historial.getPrecioNeto())
                .disponible(historial.getDisponible())
                .registradoPorId(historial.getRegistradoPor() != null ? historial.getRegistradoPor().getId() : null)
                .registradoPorNombre(historial.getRegistradoPor() != null ? historial.getRegistradoPor().getNombre() : null)
                .registradoPorRol(historial.getRegistradoPor() != null && historial.getRegistradoPor().getRol() != null ?
                        historial.getRegistradoPor().getRol().name() : null)
                .fecha(historial.getFecha())
                .build();
    }
}
