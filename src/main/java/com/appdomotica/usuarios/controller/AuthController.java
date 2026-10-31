package com.appdomotica.usuarios.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.config.JwtUtils;
import com.appdomotica.usuarios.dto.JwtResponse;
import com.appdomotica.usuarios.dto.LoginRequest;
import com.appdomotica.usuarios.dto.TokenRefreshRequest;
import com.appdomotica.usuarios.dto.TokenRefreshResponse;
import com.appdomotica.usuarios.dto.UsuarioResponse;
import com.appdomotica.usuarios.entity.RefreshToken;
import com.appdomotica.usuarios.service.InvalidRefreshTokenException;
import com.appdomotica.usuarios.service.LoginAttemptService;
import com.appdomotica.usuarios.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Autowired
    private LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String telefono = loginRequest.getTelefono() == null ? "" : loginRequest.getTelefono().trim();
        String ip = request.getRemoteAddr();

        if (loginAttemptService.bloqueado(telefono, ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(loginAttemptService.segundosDeEspera()))
                    .body(Map.of("error", "Demasiados intentos",
                            "mensaje", "Se superó el número de intentos. Espera unos minutos e inténtalo de nuevo."));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(telefono, loginRequest.getPassword()));

            String jwt = jwtUtils.generateJwtToken(authentication);
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

            loginAttemptService.registrar(telefono, ip, true);
            return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken()));

        } catch (AuthenticationException e) {
            // Mismo mensaje para teléfono inexistente, contraseña incorrecta o cuenta desactivada:
            // no se revela qué cuentas existen.
            loginAttemptService.registrar(telefono, ip, false);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas",
                            "mensaje", "El teléfono o la contraseña son incorrectos."));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshtoken(@RequestBody TokenRefreshRequest request) {
        try {
            RefreshToken nuevo = refreshTokenService.rotar(request.getRefreshToken());
            String token = jwtUtils.generateTokenFromUsuario(nuevo.getUsuario());
            return ResponseEntity.ok(new TokenRefreshResponse(token, nuevo.getToken()));
        } catch (InvalidRefreshTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Sesión inválida",
                            "mensaje", "La sesión expiró o ya no es válida. Inicia sesión de nuevo."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        refreshTokenService.revocarTodos(userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUsuario() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UsuarioResponse.fromEntity(userDetails.getUsuario()));
    }
}
