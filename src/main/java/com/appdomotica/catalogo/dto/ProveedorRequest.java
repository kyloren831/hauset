package com.appdomotica.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorRequest {
    @NotBlank(message = "El nombre del proveedor es obligatorio")
    private String nombre;

    /** Categorías que atiende, separadas por coma (p. ej. "seguridad, control_acceso"). */
    @Size(max = 255)
    private String categoriasAsociadas;

    @NotBlank(message = "El contacto del proveedor es obligatorio")
    private String contacto;
}
