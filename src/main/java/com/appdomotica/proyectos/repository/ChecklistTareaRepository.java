package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.ChecklistTarea;
import com.appdomotica.proyectos.entity.EstadoTarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistTareaRepository extends JpaRepository<ChecklistTarea, UUID> {
    List<ChecklistTarea> findByInstruccionVersionIdOrderByOrdenAsc(UUID instruccionVersionId);

    /** Lista vigente del proyecto (de cualquier versión de instrucciones, RN-06). */
    @org.springframework.data.jpa.repository.Query("select t from ChecklistTarea t "
            + "where t.instruccionVersion.proyecto.id = :proyecto and t.vigente = true "
            + "order by t.instruccionVersion.numeroVersion, t.orden")
    List<ChecklistTarea> vigentesDelProyecto(@org.springframework.data.repository.query.Param("proyecto") UUID proyecto);

    /** Avance por proyecto: [proyectoId, total vigentes, terminadas]. */
    @org.springframework.data.jpa.repository.Query("select t.instruccionVersion.proyecto.id, count(t), "
            + "sum(case when t.estado <> com.appdomotica.proyectos.entity.EstadoTarea.PENDIENTE then 1 else 0 end) "
            + "from ChecklistTarea t where t.vigente = true and t.instruccionVersion.proyecto.id in :ids "
            + "group by t.instruccionVersion.proyecto.id")
    List<Object[]> avance(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    /** Avance por visita: [visitaId, tareas completadas o "no aplica" en ella]. */
    @org.springframework.data.jpa.repository.Query("select t.completadaEnVisitaId, count(t) from ChecklistTarea t "
            + "where t.completadaEnVisitaId in :ids "
            + "and t.estado <> com.appdomotica.proyectos.entity.EstadoTarea.PENDIENTE group by t.completadaEnVisitaId")
    List<Object[]> avancePorVisita(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    List<ChecklistTarea> findByCompletadaEnVisitaId(UUID visitaId);

    List<ChecklistTarea> findByInstruccionVersionIdAndEstado(UUID instruccionVersionId, EstadoTarea estado);
}
