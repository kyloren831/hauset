#!/bin/bash
BASE_DIR="src/main/java/com/appdomotica"

# 1. Flyway migration V2
cat << 'SQL' > "src/main/resources/db/migration/V2__refresh_token.sql"
CREATE TABLE refresh_tokens (
    id SERIAL PRIMARY KEY,
    usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
    token VARCHAR NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL
);
SQL

# 2. RefreshToken Entity
cat << 'JAVA' > "$BASE_DIR/usuarios/entity/RefreshToken.java"
package com.appdomotica.usuarios.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "usuario_id", referencedColumnName = "id")
    private Usuario usuario;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }
}
JAVA

# 3. RefreshTokenRepository
cat << 'JAVA' > "$BASE_DIR/usuarios/repository/RefreshTokenRepository.java"
package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);
    int deleteByUsuario(Usuario usuario);
}
JAVA

# 4. RefreshTokenService
cat << 'JAVA' > "$BASE_DIR/usuarios/service/RefreshTokenService.java"
package com.appdomotica.usuarios.service;

import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.repository.RefreshTokenRepository;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Value("${app.jwtRefreshExpirationMs:86400000}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken createRefreshToken(Integer userId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuarioRepository.findById(userId).get());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }
}
JAVA

# 5. DTOs
cat << 'JAVA' > "$BASE_DIR/usuarios/dto/TokenRefreshRequest.java"
package com.appdomotica.usuarios.dto;

public class TokenRefreshRequest {
    private String refreshToken;
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
JAVA

cat << 'JAVA' > "$BASE_DIR/usuarios/dto/TokenRefreshResponse.java"
package com.appdomotica.usuarios.dto;

public class TokenRefreshResponse {
    private String accessToken;
    private String refreshToken;

    public TokenRefreshResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
JAVA

# 6. Update JwtResponse
cat << 'JAVA' > "$BASE_DIR/usuarios/dto/JwtResponse.java"
package com.appdomotica.usuarios.dto;

public class JwtResponse {
    private String token;
    private String refreshToken;

    public JwtResponse(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
JAVA

# 7. Update AuthController
cat << 'JAVA' > "$BASE_DIR/usuarios/controller/AuthController.java"
package com.appdomotica.usuarios.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.config.JwtUtils;
import com.appdomotica.usuarios.dto.JwtResponse;
import com.appdomotica.usuarios.dto.LoginRequest;
import com.appdomotica.usuarios.dto.TokenRefreshRequest;
import com.appdomotica.usuarios.dto.TokenRefreshResponse;
import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getTelefono(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshtoken(@RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUsuario)
                .map(usuario -> {
                    String token = jwtUtils.generateTokenFromUsername(usuario.getTelefono());
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }
}
JAVA

