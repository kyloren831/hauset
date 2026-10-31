package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("update RefreshToken r set r.revocado = true where r.familiaId = :familiaId and r.revocado = false")
    int revocarFamilia(@Param("familiaId") UUID familiaId);

    @Modifying
    @Query("update RefreshToken r set r.revocado = true where r.usuario.id = :usuarioId and r.revocado = false")
    int revocarTodosDelUsuario(@Param("usuarioId") UUID usuarioId);
}
