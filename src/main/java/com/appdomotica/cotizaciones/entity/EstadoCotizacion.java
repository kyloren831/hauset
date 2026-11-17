package com.appdomotica.cotizaciones.entity;

/**
 * borrador → enviada → aceptada | rechazada; aceptada → desactivada (para poder recotizar, RF-COT-21).
 * Los ítems son inmutables desde ENVIADA (RN-04).
 */
public enum EstadoCotizacion {
    BORRADOR, ENVIADA, ACEPTADA, RECHAZADA, DESACTIVADA
}
