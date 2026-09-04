package com.appdomotica.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${app.jwtSecret:======================AppDomotica=Secret=Key======================}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs:3600000}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication) {
        // 1. Extraer el usuario principal del objeto Authentication
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();

        // 2. Definir los atributos adicionales que quieres guardar en el JWT (Claims)
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userPrincipal.getId());
        claims.put("nombre", userPrincipal.getNombre());
        claims.put("rol", userPrincipal.getRol());

        return generateTokenWithClaims(userPrincipal.getUsername(), claims);
    }

    public String generateTokenFromUsername(String username) {
        return generateTokenWithClaims(username, new HashMap<>());
    }

    private String generateTokenWithClaims(String username, Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key())
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser().verifyWith(key()).build()
                   .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException e) {
            // log error
        }
        return false;
    }
}
