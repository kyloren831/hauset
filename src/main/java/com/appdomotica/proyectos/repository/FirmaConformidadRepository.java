package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.FirmaConformidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FirmaConformidadRepository extends JpaRepository<FirmaConformidad, UUID> {
    Optional<FirmaConformidad> findByVisitaId(UUID visitaId);

    Optional<FirmaConformidad> findFirstByVisitaProyectoId(UUID proyectoId);
}
