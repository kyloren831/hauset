package com.appdomotica.usuarios.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.usuarios.dto.PerfilRequests;
import com.appdomotica.usuarios.dto.SesionRenovadaResponse;
import com.appdomotica.usuarios.dto.UsuarioResponse;
import com.appdomotica.usuarios.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * La propia cuenta, para cualquier rol: contraseña, teléfono y foto. Siempre actúa sobre quien
 * inició sesión; no recibe ids. El perfil se consulta en {@code GET /api/usuarios/me}.
 * La foto de cualquier compañero se ve con {@code GET /api/usuarios/{id}/foto}.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @PutMapping("/me/password")
    public SesionRenovadaResponse cambiarPassword(@AuthenticationPrincipal CustomUserDetails yo,
                                                  @Valid @RequestBody PerfilRequests.CambioPassword request) {
        return perfilService.cambiarPassword(yo.getId(), request);
    }

    @PutMapping("/me/telefono")
    public SesionRenovadaResponse cambiarTelefono(@AuthenticationPrincipal CustomUserDetails yo,
                                                  @Valid @RequestBody PerfilRequests.CambioTelefono request) {
        return perfilService.cambiarTelefono(yo.getId(), request);
    }

    @PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UsuarioResponse cambiarFoto(@AuthenticationPrincipal CustomUserDetails yo,
                                       @RequestParam("file") MultipartFile file) {
        return perfilService.cambiarFoto(yo.getId(), file);
    }

    @DeleteMapping("/me/foto")
    public UsuarioResponse quitarFoto(@AuthenticationPrincipal CustomUserDetails yo) {
        return perfilService.quitarFoto(yo.getId());
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> verFoto(@PathVariable UUID id) {
        ArchivoAdjunto foto = perfilService.foto(id);
        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(foto.getMimeType());
        } catch (Exception e) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        // Solo se sirve como imagen; cualquier otra cosa se descarga y el navegador no la interpreta.
        if (!"image".equals(tipo.getType())) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(tipo)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(perfilService.contenido(foto));
    }
}
