package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.IntentoLogin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface IntentoLoginRepository extends JpaRepository<IntentoLogin, UUID> {

    long countByTelefonoAndExitosoFalseAndFechaAfter(String telefono, Instant desde);

    long countByIpAndExitosoFalseAndFechaAfter(String ip, Instant desde);
}
