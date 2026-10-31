package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.EstadoCatalogo;
import jakarta.validation.constraints.NotNull;

/** Activar o desactivar. Archivar y restaurar tienen su propio endpoint (solo jefes). */
public record CambioEstadoRequest(@NotNull(message = "El estado es obligatorio") EstadoCatalogo estado) {
}
