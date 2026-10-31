package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.EstadoVisita;
import com.appdomotica.proyectos.entity.Visita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VisitaRepository extends JpaRepository<Visita, UUID> {
    List<Visita> findByProyectoIdOrderByNumeroVisitaAsc(UUID proyectoId);

    List<Visita> findByProyectoIdAndEstadoIn(UUID proyectoId, Collection<EstadoVisita> estados);

    Optional<Visita> findFirstByProyectoIdAndEstado(UUID proyectoId, EstadoVisita estado);

    @Query("select coalesce(max(v.numeroVisita), 0) from Visita v where v.proyecto.id = :proyecto")
    int ultimoNumero(@Param("proyecto") UUID proyecto);

    /** Agendadas de una persona que empiezan en [desde, hasta), para buscar cruces (RF-AGE-03). */
    @Query("select v from Visita v join fetch v.proyecto where v.asignadoA.id = :persona "
            + "and v.estado = com.appdomotica.proyectos.entity.EstadoVisita.AGENDADA "
            + "and v.fechaHora >= :desde and v.fechaHora < :hasta order by v.fechaHora")
    List<Visita> agendadasDeEntre(@Param("persona") UUID persona, @Param("desde") OffsetDateTime desde,
                                  @Param("hasta") OffsetDateTime hasta);

    /** Agenda compartida: visitas (no canceladas ni sin fecha) que empiezan en [desde, hasta) (RF-AGE-01). */
    @Query("select v from Visita v join fetch v.proyecto p left join fetch v.asignadoA "
            + "where v.fechaHora >= :desde and v.fechaHora < :hasta and v.estado not in "
            + "(com.appdomotica.proyectos.entity.EstadoVisita.CANCELADA, "
            + "com.appdomotica.proyectos.entity.EstadoVisita.PENDIENTE_AGENDAR) order by v.fechaHora")
    List<Visita> deEntre(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);

    /** Retornos y visitas que esperan fecha y persona (RF-AGE-04). */
    @Query("select v from Visita v join fetch v.proyecto where v.estado = "
            + "com.appdomotica.proyectos.entity.EstadoVisita.PENDIENTE_AGENDAR order by v.createdAt")
    List<Visita> porAgendar();

    /** Visitas por venir (sin agendar o agendadas) de varios proyectos, para el listado. */
    @Query("select v from Visita v where v.proyecto.id in :ids and v.estado in "
            + "(com.appdomotica.proyectos.entity.EstadoVisita.PENDIENTE_AGENDAR, "
            + "com.appdomotica.proyectos.entity.EstadoVisita.AGENDADA)")
    List<Visita> porVenir(@Param("ids") Collection<UUID> ids);
}
