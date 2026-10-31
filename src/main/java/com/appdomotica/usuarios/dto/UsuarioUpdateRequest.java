package com.appdomotica.usuarios.dto;

import com.appdomotica.usuarios.entity.RolUsuario;
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
public class UsuarioUpdateRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El rol es obligatorio")
    private RolUsuario rol;

    @NotBlank(message = "El teléfono es obligatorio")
    private String telefono;

    private String password;
}
