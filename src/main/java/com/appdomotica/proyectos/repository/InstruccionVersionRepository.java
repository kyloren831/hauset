package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.InstruccionVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstruccionVersionRepository extends JpaRepository<InstruccionVersion, UUID> {
    List<InstruccionVersion> findByProyectoIdOrderByNumeroVersionDesc(UUID proyectoId);
    Optional<InstruccionVersion> findByProyectoIdAndNumeroVersion(UUID proyectoId, Integer numeroVersion);
}
