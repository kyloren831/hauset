package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);
    int deleteByUsuario(Usuario usuario);
}
