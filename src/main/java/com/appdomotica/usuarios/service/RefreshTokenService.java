package com.appdomotica.usuarios.service;

import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.RefreshTokenRepository;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Emite, rota y revoca refresh tokens. Cada login abre una "familia"; cada uso entrega un
 * token nuevo y deja el anterior revocado. Reutilizar uno ya rotado revoca toda la familia.
 */
@Service
public class RefreshTokenService {
    @Value("${app.jwtRefreshExpirationMs:604800000}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Primer token de una sesión nueva (login). */
    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Usuario inexistente"));
        return emitir(usuario, UUID.randomUUID());
    }

    /**
     * Consume un refresh token y entrega el siguiente de la misma familia.
     * Se conserva la transacción aunque falle, para que la revocación por reutilización quede guardada.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshToken rotar(String tokenValue) {
        RefreshToken actual = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Token inexistente"));

        if (actual.isRevocado()) {
            refreshTokenRepository.revocarFamilia(actual.getFamiliaId());
            throw new InvalidRefreshTokenException("Token reutilizado");
        }
        if (actual.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.revocarFamilia(actual.getFamiliaId());
            throw new InvalidRefreshTokenException("Token expirado");
        }
        Usuario usuario = actual.getUsuario();
        if (usuario.getActivo() != null && !usuario.getActivo()) {
            refreshTokenRepository.revocarTodosDelUsuario(usuario.getId());
            throw new InvalidRefreshTokenException("Usuario desactivado");
        }

        actual.setRevocado(true);
        refreshTokenRepository.save(actual);
        return emitir(usuario, actual.getFamiliaId());
    }

    /** Cierra todas las sesiones del usuario (logout o desactivación). */
    @Transactional
    public int revocarTodos(UUID usuarioId) {
        return refreshTokenRepository.revocarTodosDelUsuario(usuarioId);
    }

    private RefreshToken emitir(Usuario usuario, UUID familiaId) {
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setFamiliaId(familiaId);
        token.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        return refreshTokenRepository.save(token);
    }
}
