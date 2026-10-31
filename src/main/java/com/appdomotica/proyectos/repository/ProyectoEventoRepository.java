package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.ProyectoEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProyectoEventoRepository extends JpaRepository<ProyectoEvento, UUID> {
    List<ProyectoEvento> findByProyectoIdOrderByFechaDesc(UUID proyectoId);
}
