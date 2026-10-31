package com.appdomotica.config;

import com.appdomotica.usuarios.entity.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    /** Base64 de al menos 32 bytes. Sin valor por defecto: si falta, la aplicación no arranca. */
    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs:900000}")
    private long jwtExpirationMs;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("app.jwtSecret debe estar en Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwtSecret debe tener al menos 32 bytes (256 bits)");
        }
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateJwtToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userPrincipal.getId());
        claims.put("nombre", userPrincipal.getNombre());
        claims.put("rol", userPrincipal.getRol());

        return generateTokenWithClaims(userPrincipal.getUsername(), claims);
    }

    public String generateTokenFromUsuario(Usuario usuario) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", usuario.getId());
        claims.put("nombre", usuario.getNombre());
        claims.put("rol", usuario.getRol().name());

        return generateTokenWithClaims(usuario.getTelefono(), claims);
    }

    private String generateTokenWithClaims(String username, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    /** Devuelve el teléfono (subject) o lanza JwtException si el token es inválido o expiró. */
    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key).build()
                   .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
