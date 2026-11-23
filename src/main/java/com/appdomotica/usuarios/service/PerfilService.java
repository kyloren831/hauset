package com.appdomotica.usuarios.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.config.JwtUtils;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.ValidadorImagen;
import com.appdomotica.usuarios.dto.PerfilRequests;
import com.appdomotica.usuarios.dto.SesionRenovadaResponse;
import com.appdomotica.usuarios.dto.UsuarioResponse;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Lo que cada persona cambia de su propia cuenta: contraseña, teléfono y foto. */
@Service
@RequiredArgsConstructor
public class PerfilService {

    public static final String ENTIDAD_USUARIO = "USUARIO";
    public static final String CATEGORIA_FOTO = "FOTO_PERFIL";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;
    private final ArchivoAdjuntoService archivos;

    @Transactional
    public SesionRenovadaResponse cambiarPassword(UUID usuarioId, PerfilRequests.CambioPassword request) {
        Usuario usuario = buscar(usuarioId);
        exigirPassword(usuario, request.getActual(), "La contraseña actual no es correcta");
        if (passwordEncoder.matches(request.getNueva(), usuario.getPasswordHash())) {
            throw new BadRequestException("La contraseña nueva debe ser distinta de la actual");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.getNueva()));
        return renovarSesion(usuarioRepository.save(usuario));
    }

    @Transactional
    public SesionRenovadaResponse cambiarTelefono(UUID usuarioId, PerfilRequests.CambioTelefono request) {
        Usuario usuario = buscar(usuarioId);
        exigirPassword(usuario, request.getPassword(), "La contraseña no es correcta");
        String telefono = request.getTelefono().trim();
        if (telefono.equals(usuario.getTelefono())) {
            throw new BadRequestException("Ese ya es tu teléfono");
        }
        if (usuarioRepository.existsByTelefono(telefono)) {
            throw new BadRequestException("Ese teléfono ya lo usa otra cuenta");
        }
        usuario.setTelefono(telefono);
        // El token lleva el teléfono como usuario: los anteriores dejan de servir.
        return renovarSesion(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarFoto(UUID usuarioId, MultipartFile foto) {
        Usuario usuario = buscar(usuarioId);
        ValidadorImagen.validar(foto);
        UUID anterior = usuario.getFotoId();
        UUID nueva = archivos.subirArchivo(foto, ENTIDAD_USUARIO, usuario.getId(), CATEGORIA_FOTO,
                usuario.getNombre(), usuario).getId();
        usuario.setFotoId(nueva);
        if (anterior != null && !anterior.equals(nueva)) {
            archivos.eliminarArchivo(anterior);
        }
        return UsuarioResponse.fromEntity(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse quitarFoto(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        UUID anterior = usuario.getFotoId();
        if (anterior != null) {
            usuario.setFotoId(null);
            archivos.eliminarArchivo(anterior);
        }
        return UsuarioResponse.fromEntity(usuarioRepository.save(usuario));
    }

    /** Foto vigente de una persona, para mostrarla a sus compañeros. Solo esa: no abre otros archivos. */
    @Transactional(readOnly = true)
    public ArchivoAdjunto foto(UUID usuarioId) {
        Usuario usuario = buscar(usuarioId);
        if (usuario.getFotoId() == null) {
            throw new ResourceNotFoundException("Esta persona no tiene foto");
        }
        return archivos.obtenerEntidad(usuario.getFotoId());
    }

    public Resource contenido(ArchivoAdjunto archivo) {
        return archivos.cargarContenido(archivo);
    }

    /** Cierra todas las sesiones (otros teléfonos incluidos) y abre una nueva para quien hizo el cambio. */
    private SesionRenovadaResponse renovarSesion(Usuario usuario) {
        refreshTokenService.revocarTodos(usuario.getId());
        String token = jwtUtils.generateTokenFromUsuario(usuario);
        String refresh = refreshTokenService.createRefreshToken(usuario.getId()).getToken();
        return new SesionRenovadaResponse(token, refresh, UsuarioResponse.fromEntity(usuario));
    }

    private void exigirPassword(Usuario usuario, String password, String mensaje) {
        if (password == null || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new BadRequestException(mensaje);
        }
    }

    private Usuario buscar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
