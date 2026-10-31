package com.appdomotica.agenda.dto;

import com.appdomotica.proyectos.entity.EstadoProyecto;
import com.appdomotica.proyectos.entity.EstadoVisita;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de la agenda compartida (RF-AGE-01..05). Sin precios. */
public final class AgendaDtos {
    private AgendaDtos() {
    }

    /**
     * Visita en la agenda. `inicio`/`fin`: lo agendado (o lo que de verdad duró, si ya terminó).
     * `conCruce`: la persona tiene otra visita agendada que se le cruza (RF-AGE-03).
     */
    public record VisitaAgenda(UUID id, UUID proyectoId, EstadoProyecto estadoProyecto, int numero,
                               EstadoVisita estado, UUID asignadoAId, String asignadoA, OffsetDateTime inicio,
                               OffsetDateTime fin, int duracionMinutos, String motivo, String clienteNombre,
                               String clienteDireccion, String clienteTelefono, boolean conCruce, long version) {
    }

    /**
     * `porAgendar`: retornos y visitas sin fecha (RF-AGE-04); vacío para quien no ve toda la agenda.
     * `todaLaAgenda`: false si solo se muestran las visitas propias (instalador, RF-AGE-05).
     */
    public record Agenda(LocalDate desde, LocalDate hasta, boolean todaLaAgenda, List<VisitaAgenda> visitas,
                         List<VisitaAgenda> porAgendar) {
    }
}
