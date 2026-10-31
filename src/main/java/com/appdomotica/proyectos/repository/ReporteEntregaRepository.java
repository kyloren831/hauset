package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.ReporteEntrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReporteEntregaRepository extends JpaRepository<ReporteEntrega, UUID> {
    Optional<ReporteEntrega> findByProyectoId(UUID proyectoId);
}
