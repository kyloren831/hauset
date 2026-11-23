package com.appdomotica.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Los límites cortan entradas absurdas antes de BCrypt; la clave no exige mínimo para no bloquear cuentas viejas. */
@Data
public class LoginRequest {
    @NotBlank(message = "Escribe tu teléfono")
    @Size(max = 50, message = "Teléfono demasiado largo")
    private String telefono;

    @NotBlank(message = "Escribe tu contraseña")
    @Size(max = 200, message = "Contraseña demasiado larga")
    private String password;
}
