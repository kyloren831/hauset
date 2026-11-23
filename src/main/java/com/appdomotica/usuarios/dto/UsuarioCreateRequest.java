package com.appdomotica.usuarios.dto;

import com.appdomotica.usuarios.entity.RolUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCreateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre puede tener como mucho 100 caracteres")
    private String nombre;

    @NotNull(message = "El rol es obligatorio")
    private RolUsuario rol;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = PerfilRequests.TELEFONO, message = PerfilRequests.TELEFONO_MENSAJE)
    private String telefono;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = PerfilRequests.PASSWORD_MIN, max = PerfilRequests.PASSWORD_MAX, message = PerfilRequests.PASSWORD_MENSAJE)
    private String password;
}
