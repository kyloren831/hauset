package com.appdomotica.catalogo.dto;

import com.appdomotica.catalogo.entity.EstadoCatalogo;

import java.util.EnumSet;
import java.util.Set;

/** Filtro de listados del catálogo. Por defecto solo activos. */
public enum FiltroEstado {
    ACTIVO, INACTIVO, ARCHIVADO, TODOS;

    public Set<EstadoCatalogo> estados() {
        return this == TODOS ? EnumSet.allOf(EstadoCatalogo.class) : EnumSet.of(EstadoCatalogo.valueOf(name()));
    }

    /** Texto de búsqueda → patrón LIKE en minúscula; vacío si no hay búsqueda. */
    public static String patron(String busqueda) {
        return busqueda == null || busqueda.isBlank() ? "" : "%" + busqueda.trim().toLowerCase() + "%";
    }
}
