package com.appdomotica.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lo que cada persona puede cambiar de su propia cuenta, y las reglas compartidas con la administración. */
public final class PerfilRequests {

    /** Teléfono de Costa Rica: 8 dígitos. Es el usuario con el que se inicia sesión. */
    public static final String TELEFONO = "^[0-9]{8}$";
    public static final String TELEFONO_MENSAJE = "El teléfono debe tener 8 dígitos";

    /** BCrypt solo usa los primeros 72 bytes: más largo no aporta y engaña. */
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;
    public static final String PASSWORD_MENSAJE = "La contraseña debe tener entre 8 y 72 caracteres";

    private PerfilRequests() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CambioPassword {
        @NotBlank(message = "Escribe tu contraseña actual")
        private String actual;

        @NotBlank(message = "Escribe la contraseña nueva")
        @Size(min = PASSWORD_MIN, max = PASSWORD_MAX, message = PASSWORD_MENSAJE)
        private String nueva;
    }

    /** Cambiar el teléfono cambia el usuario de inicio de sesión: se confirma con la contraseña. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CambioTelefono {
        @NotBlank(message = "El teléfono es obligatorio")
        @Pattern(regexp = TELEFONO, message = TELEFONO_MENSAJE)
        private String telefono;

        @NotBlank(message = "Escribe tu contraseña para confirmar")
        private String password;
    }
}
