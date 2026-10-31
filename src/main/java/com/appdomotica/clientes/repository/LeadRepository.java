package com.appdomotica.clientes.repository;

import com.appdomotica.clientes.entity.EstadoLead;
import com.appdomotica.clientes.entity.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    Page<Lead> findByEstado(EstadoLead estado, Pageable pageable);

    long countByEstado(EstadoLead estado);
}
