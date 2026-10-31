package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.EstadoProyecto;
import com.appdomotica.proyectos.entity.Proyecto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, UUID> {
    Optional<Proyecto> findByCotizacionId(UUID cotizacionId);

    @org.springframework.data.jpa.repository.Query("select p.cotizacion.id, p.id from Proyecto p where p.cotizacion.id in :ids")
    java.util.List<Object[]> idsPorCotizacion(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    List<Proyecto> findAllByOrderByFechaCreacionDesc();
    List<Proyecto> findByEstado(EstadoProyecto estado);

    /** Bloquea la fila del proyecto: visitas y cierre no se pisan entre dos teléfonos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proyecto p where p.id = :id")
    Optional<Proyecto> bloquear(@Param("id") UUID id);
}
