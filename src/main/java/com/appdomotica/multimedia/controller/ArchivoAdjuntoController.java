package com.appdomotica.multimedia.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.usuarios.entity.RolUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Descarga de adjuntos por id (fotos de tareas, ubicaciones, productos, firmas y reportes).
 * Subir y borrar solo se hace por las rutas de cada dominio, que validan rol, estado y tipo de archivo:
 * una ruta genérica permitía inyectar evidencia, reemplazar el PDF enviado de una cotización y borrar
 * el reporte de entrega de un proyecto cerrado (auditoría, pista 1).
 */
@RestController
@RequestMapping("/api/v1/archivos")
@PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR', 'SECRETARIA')")
@RequiredArgsConstructor
public class ArchivoAdjuntoController {

    /** Entidades cuyos archivos llevan precios: solo los roles que ven cotizaciones. */
    private static final Map<String, Set<RolUsuario>> RESTRINGIDOS = Map.of(
            "COTIZACION", EnumSet.of(RolUsuario.JEFE_SISTEMAS, RolUsuario.JEFE_ELECTROMECANICO, RolUsuario.SECRETARIA));

    private final ArchivoAdjuntoService archivoService;

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable UUID id,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        ArchivoAdjunto adjunto = archivoService.obtenerEntidad(id);
        Set<RolUsuario> permitidos = RESTRINGIDOS.get(adjunto.getEntidadTipo());
        if (permitidos != null && !permitidos.contains(userDetails.getUsuario().getRol())) {
            throw new AccessDeniedException("Sin permiso para este archivo");
        }
        Resource recurso = archivoService.cargarContenido(adjunto);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(adjunto.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + adjunto.getNombreOriginal() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(recurso);
    }
}
