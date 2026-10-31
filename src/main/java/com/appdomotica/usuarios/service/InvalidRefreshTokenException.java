package com.appdomotica.usuarios.service;

/** El refresh token no existe, expiró, fue revocado o reutilizado. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
