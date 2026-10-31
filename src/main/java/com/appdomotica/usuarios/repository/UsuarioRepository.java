package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByTelefono(String telefono);
    boolean existsByTelefono(String telefono);
    List<Usuario> findByRol(RolUsuario rol);
    List<Usuario> findByActivo(Boolean activo);
    List<Usuario> findByRolAndActivo(RolUsuario rol, Boolean activo);
}
