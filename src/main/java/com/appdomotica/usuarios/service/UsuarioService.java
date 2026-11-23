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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Administración de cuentas: solo los dos jefes (el controlador lo exige). */
@Service
public class UsuarioService {

    static final Set<RolUsuario> JEFES = EnumSet.of(RolUsuario.JEFE_SISTEMAS, RolUsuario.JEFE_ELECTROMECANICO);

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
                .sorted(Comparator.comparing(u -> u.getNombre() == null ? "" : u.getNombre().toLowerCase()))
                .map(UsuarioResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(UUID id) {
        return UsuarioResponse.fromEntity(buscar(id));
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        String telefono = request.getTelefono().trim();
        if (usuarioRepository.existsByTelefono(telefono)) {
            throw new BadRequestException("Ya existe un usuario registrado con el teléfono: " + telefono);
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        usuario.setRol(request.getRol());
        usuario.setTelefono(telefono);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(guardado);
    }

    @Transactional
    public UsuarioResponse actualizarUsuario(UUID id, UsuarioUpdateRequest request, UUID actorId) {
        Usuario usuario = buscar(id);
        String telefono = request.getTelefono().trim();

        if (!usuario.getTelefono().equals(telefono) && usuarioRepository.existsByTelefono(telefono)) {
            throw new BadRequestException("Ya existe otro usuario con el teléfono: " + telefono);
        }
        if (usuario.getId().equals(actorId) && !JEFES.contains(request.getRol())) {
            throw new BadRequestException("No puedes quitarte el rol de jefe; pídeselo al otro jefe");
        }
        exigirOtroJefeActivo(usuario, request.getRol(), usuario.getActivo());

        usuario.setNombre(request.getNombre().trim());
        usuario.setRol(request.getRol());
        usuario.setTelefono(telefono);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            // Una contraseña nueva invalida las sesiones abiertas.
            refreshTokenService.revocarTodos(usuario.getId());
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(actualizado);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(UUID id, Boolean activo, UUID actorId) {
        Usuario usuario = buscar(id);
        if (Boolean.FALSE.equals(activo) && usuario.getId().equals(actorId)) {
            throw new BadRequestException("No puedes desactivar tu propia cuenta");
        }
        exigirOtroJefeActivo(usuario, usuario.getRol(), activo);

        usuario.setActivo(activo);
        if (Boolean.FALSE.equals(activo)) {
            refreshTokenService.revocarTodos(usuario.getId());
        }
        Usuario actualizado = usuarioRepository.save(usuario);
        return UsuarioResponse.fromEntity(actualizado);
    }

    /**
     * Sin un jefe activo nadie podría volver a administrar cuentas: si el cambio deja de contar a
     * este usuario como jefe activo, tiene que quedar otro.
     */
    private void exigirOtroJefeActivo(Usuario usuario, RolUsuario rolNuevo, Boolean activoNuevo) {
        boolean eraJefeActivo = JEFES.contains(usuario.getRol()) && Boolean.TRUE.equals(usuario.getActivo());
        boolean seraJefeActivo = JEFES.contains(rolNuevo) && Boolean.TRUE.equals(activoNuevo);
        if (eraJefeActivo && !seraJefeActivo && usuarioRepository.countByRolInAndActivoTrue(JEFES) <= 1) {
            throw new BadRequestException("Debe quedar al menos un jefe activo");
        }
    }

    private Usuario buscar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }
}
