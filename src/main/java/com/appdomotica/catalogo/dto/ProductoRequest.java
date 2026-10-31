package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRequest {
    @NotBlank(message = "El nombre del producto es obligatorio")
    private String nombre;

    @NotNull(message = "La categoría del producto es obligatoria")
    private CategoriaProducto categoria;

    private String especificacion;
}
