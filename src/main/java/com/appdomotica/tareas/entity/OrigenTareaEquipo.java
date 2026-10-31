package com.appdomotica.tareas.entity;

/**
 * Quién creó la tarea: una persona, o el sistema por un evento del flujo (RF-TAR-05).
 * CAMBIO_PROPUESTO: un cambio de instrucciones espera a un jefe. EXTRA_PENDIENTE: un extra de
 * materiales espera precios y confirmación. Las automáticas se cierran solas al resolverse.
 */
public enum OrigenTareaEquipo {
    MANUAL, COTIZACION_ACEPTADA, CAMBIO_PROPUESTO, EXTRA_PENDIENTE
}
