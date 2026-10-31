package com.appdomotica.common.exception;

/** La operación no es válida en el estado actual del recurso (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
