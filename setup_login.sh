#!/bin/bash
BASE_DIR="src/main/java/com/appdomotica"

# 1. Update SecurityConfig to provide AuthenticationManager and PasswordEncoder
cat << 'JAVA' > "$BASE_DIR/config/SecurityConfig.java"
package com.appdomotica.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/usuarios/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
JAVA

# 2. Add findByTelefono to UsuarioRepository
cat << 'JAVA' > "$BASE_DIR/usuarios/repository/UsuarioRepository.java"
package com.appdomotica.usuarios.repository;

import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByTelefono(String telefono);
}
JAVA

# 3. Create CustomUserDetails implementation
cat << 'JAVA' > "$BASE_DIR/config/CustomUserDetails.java"
package com.appdomotica.config;

import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final Usuario usuario;

    public CustomUserDetails(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getTelefono();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return usuario.getActivo() != null ? usuario.getActivo() : true;
    }

    public Integer getId() {
        return usuario.getId();
    }
}
JAVA

# 4. Create CustomUserDetailsService
cat << 'JAVA' > "$BASE_DIR/config/CustomUserDetailsService.java"
package com.appdomotica.config;

import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String telefono) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByTelefono(telefono)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con teléfono: " + telefono));
        return new CustomUserDetails(usuario);
    }
}
JAVA

# 5. Create DTOs
mkdir -p "$BASE_DIR/usuarios/dto"
cat << 'JAVA' > "$BASE_DIR/usuarios/dto/LoginRequest.java"
package com.appdomotica.usuarios.dto;
import lombok.Data;
@Data
public class LoginRequest {
    private String telefono;
    private String password;
}
JAVA

cat << 'JAVA' > "$BASE_DIR/usuarios/dto/JwtResponse.java"
package com.appdomotica.usuarios.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
}
JAVA

# 6. Create AuthController
cat << 'JAVA' > "$BASE_DIR/usuarios/controller/AuthController.java"
package com.appdomotica.usuarios.controller;

import com.appdomotica.config.JwtUtils;
import com.appdomotica.usuarios.dto.JwtResponse;
import com.appdomotica.usuarios.dto.LoginRequest;
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

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getTelefono(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        return ResponseEntity.ok(new JwtResponse(jwt));
    }
}
JAVA

