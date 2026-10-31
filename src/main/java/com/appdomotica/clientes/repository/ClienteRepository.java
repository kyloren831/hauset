package com.appdomotica.clientes.repository;

import com.appdomotica.clientes.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    Page<Cliente> findByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Cliente> findByActivoTrue(Pageable pageable);
}
