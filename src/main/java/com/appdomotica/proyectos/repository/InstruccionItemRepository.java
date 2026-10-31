package com.appdomotica.proyectos.repository;

import com.appdomotica.proyectos.entity.InstruccionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstruccionItemRepository extends JpaRepository<InstruccionItem, UUID> {
    List<InstruccionItem> findByInstruccionVersionIdOrderByOrdenAsc(UUID instruccionVersionId);
}
