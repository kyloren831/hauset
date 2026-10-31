package com.appdomotica.usuarios.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.usuarios.dto.UsuarioCreateRequest;
import com.appdomotica.usuarios.dto.UsuarioResponse;
import com.appdomotica.usuarios.dto.UsuarioUpdateRequest;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarUsuarios(RolUsuario rol, Boolean activo) {
        List<Usuario> usuarios;
        if (rol != null && activo != null) {
            usuarios = usuarioRepository.findByRolAndActivo(rol, activo);
        } else if (rol != null) {
            usuarios = usuarioRepository.findByRol(rol);
        } else if (activo != null) {
            usuarios = usuarioRepository.findByActivo(activo);
        } else {
            usuarios = usuarioRepository.findAll();
        }

        return usuarios.stream()
                .map(UsuarioResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return UsuarioResponse.fromEntity(usuario);
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        if (usuarioRepository.existsByTelefono(request.getTelefono())) {
            throw new BadRequestException("Ya existe un usuario registrado con el teléfono: " + request.getTelefono());
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setRol(request.getRol());
        usuario.setTelefono(request.getTelefono());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(guardado);
    }

    @Transactional
    public UsuarioResponse actualizarUsuario(UUID id, UsuarioUpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (!usuario.getTelefono().equals(request.getTelefono()) && usuarioRepository.existsByTelefono(request.getTelefono())) {
            throw new BadRequestException("Ya existe otro usuario con el teléfono: " + request.getTelefono());
        }

        usuario.setNombre(request.getNombre());
        usuario.setRol(request.getRol());
        usuario.setTelefono(request.getTelefono());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            // Una contraseña nueva invalida las sesiones abiertas.
            refreshTokenService.revocarTodos(usuario.getId());
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(actualizado);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, Boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        usuario.setActivo(activo);
        if (Boolean.FALSE.equals(activo)) {
            refreshTokenService.revocarTodos(usuario.getId());
        }
        Usuario actualizado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(actualizado);
    }
}
