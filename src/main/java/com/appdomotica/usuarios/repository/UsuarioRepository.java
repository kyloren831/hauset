package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByTelefono(String telefono);
}
