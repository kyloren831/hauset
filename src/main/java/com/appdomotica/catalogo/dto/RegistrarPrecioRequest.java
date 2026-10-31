package com.appdomotica.catalogo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarPrecioRequest {
    @NotNull(message = "El ID del producto es obligatorio")
    private UUID productoId;

    @NotNull(message = "El ID del proveedor es obligatorio")
    private UUID proveedorId;

    @NotNull(message = "El precio neto es obligatorio")
    @Positive(message = "El precio neto debe ser un valor positivo mayor a cero")
    private BigDecimal precioNeto;

    @Builder.Default
    private Boolean disponible = true;
}
