package com.appdomotica.tareas.repository;

import com.appdomotica.tareas.entity.EstadoTareaEquipo;
import com.appdomotica.tareas.entity.OrigenTareaEquipo;
import com.appdomotica.tareas.entity.TareaEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TareaEquipoRepository extends JpaRepository<TareaEquipo, UUID> {

    /** Lo que ve una persona: las públicas y sus privadas; de las hechas, solo las recientes. */
    @Query("""
            select t from TareaEquipo t
            where (t.privada = false or t.creadaPor.id = :yo)
              and (t.estado <> com.appdomotica.tareas.entity.EstadoTareaEquipo.HECHA or t.completadaEn >= :hechasDesde)
            order by t.createdAt desc
            """)
    List<TareaEquipo> visiblesPara(@Param("yo") UUID yo, @Param("hechasDesde") OffsetDateTime hechasDesde);

    List<TareaEquipo> findByCotizacionIdAndOrigenAndEstadoNot(UUID cotizacionId, OrigenTareaEquipo origen,
                                                              EstadoTareaEquipo estado);

    List<TareaEquipo> findByOrigenAndOrigenRefAndEstadoNot(OrigenTareaEquipo origen, UUID origenRef,
                                                           EstadoTareaEquipo estado);
}
