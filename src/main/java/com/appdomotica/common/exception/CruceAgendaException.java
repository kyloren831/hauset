package com.appdomotica.common.exception;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La persona ya tiene otra visita en ese horario (RF-AGE-03). Es un aviso: no se guardó nada y quien agenda
 * puede volver a enviar con `confirmarCruce = true`. Responde HTTP 409 con `codigo = CRUCE_AGENDA`.
 */
public class CruceAgendaException extends RuntimeException {

    /** Visita con la que se cruza. */
    public record Cruce(UUID visitaId, UUID proyectoId, int numero, String cliente, String persona,
                        OffsetDateTime inicio, OffsetDateTime fin) {
    }

    private final transient List<Cruce> cruces;

    public CruceAgendaException(String mensaje, List<Cruce> cruces) {
        super(mensaje);
        this.cruces = List.copyOf(cruces);
    }

    public List<Cruce> getCruces() {
        return cruces;
    }
}
