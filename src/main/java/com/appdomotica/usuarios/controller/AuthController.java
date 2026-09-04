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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getTelefono(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

            return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas", "mensaje", "El teléfono o la contraseña son incorrectos."));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cuenta deshabilitada", "mensaje", "Tu cuenta ha sido desactivada. Contacta al administrador."));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario no encontrado", "mensaje", "No existe una cuenta con ese número de teléfono."));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshtoken(@RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUsuario)
                .map(usuario -> {
                    String token = jwtUtils.generateTokenFromUsername(usuario.getTelefono());
                    return ResponseEntity.ok((Object) new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Token inválido", "mensaje", "El refresh token no existe o ya fue utilizado.")));
    }
}
