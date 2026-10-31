package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.CambioInstruccion;
import com.appdomotica.proyectos.entity.EstadoCambio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CambioInstruccionRepository extends JpaRepository<CambioInstruccion, UUID> {
    List<CambioInstruccion> findByProyectoId(UUID proyectoId);
    List<CambioInstruccion> findByProyectoIdAndEstado(UUID proyectoId, EstadoCambio estado);

    /** Solo el id del proyecto, sin cargar el cambio (para bloquear el proyecto antes de leerlo). */
    @org.springframework.data.jpa.repository.Query("select c.proyecto.id from CambioInstruccion c where c.id = :id")
    java.util.Optional<UUID> proyectoDe(@org.springframework.data.repository.query.Param("id") UUID id);
}
