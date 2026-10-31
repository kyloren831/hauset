package com.appdomotica.proyectos.entity;

/**
 * PENDIENTE_AGENDAR: retorno sin fecha ni persona (RF-AGE-04). AGENDADA → EN_CURSO (el equipo llegó) →
 * COMPLETADA o REQUIERE_RETORNO (con motivo). CANCELADA: agendada que ya no se hará (p. ej. al cerrar).
 */
public enum EstadoVisita {
    PENDIENTE_AGENDAR,
    AGENDADA,
    EN_CURSO,
    COMPLETADA,
    REQUIERE_RETORNO,
    CANCELADA
}
