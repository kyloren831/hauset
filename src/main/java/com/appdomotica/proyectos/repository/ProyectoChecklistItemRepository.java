package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.ProyectoChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProyectoChecklistItemRepository extends JpaRepository<ProyectoChecklistItem, UUID> {
    List<ProyectoChecklistItem> findByProyectoIdOrderByOrdenAsc(UUID proyectoId);

    /** Avance por proyecto: [proyectoId, ítems, marcados]. */
    @Query("select i.proyecto.id, count(i), sum(case when i.marcado = true then 1 else 0 end) "
            + "from ProyectoChecklistItem i where i.proyecto.id in :ids group by i.proyecto.id")
    List<Object[]> avance(@Param("ids") Collection<UUID> ids);

    /** Marcados en cada visita: [visitaId, ítems]. */
    @Query("select i.marcadoEnVisitaId, count(i) from ProyectoChecklistItem i "
            + "where i.marcado = true and i.marcadoEnVisitaId in :ids group by i.marcadoEnVisitaId")
    List<Object[]> avancePorVisita(@Param("ids") Collection<UUID> ids);
}
