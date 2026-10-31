package com.appdomotica.catalogo.entity;

/**
 * Ciclo de vida de productos y proveedores. Solo ACTIVO se ofrece para informes y cotizaciones nuevas;
 * INACTIVO sigue visible y editable; ARCHIVADO queda oculto por defecto y de solo lectura.
 */
public enum EstadoCatalogo { ACTIVO, INACTIVO, ARCHIVADO }
