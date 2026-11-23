package com.appdomotica.usuarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta de los cambios que cierran las demás sesiones (contraseña, teléfono): la app reemplaza
 * sus tokens por estos y sigue con la sesión abierta en este teléfono.
 */
@Data
@AllArgsConstructor
public class SesionRenovadaResponse {
    private String token;
    private String refreshToken;
    private UsuarioResponse usuario;
}
