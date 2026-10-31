package com.appdomotica.multimedia.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.multimedia.dto.ArchivoAdjuntoResponse;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/archivos")
@PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR', 'SECRETARIA')")
@RequiredArgsConstructor
public class ArchivoAdjuntoController {

    private final ArchivoAdjuntoService archivoService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoAdjuntoResponse> subirArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entidadTipo") String entidadTipo,
            @RequestParam("entidadId") UUID entidadId,
            @RequestParam("categoria") String categoria,
            @RequestParam(value = "notas", required = false) String notas,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ArchivoAdjuntoResponse response = archivoService.subirArchivo(
                file, entidadTipo, entidadId, categoria, notas,
                userDetails != null ? userDetails.getUsuario() : null
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/entidad/{entidadTipo}/{entidadId}")
    public ResponseEntity<List<ArchivoAdjuntoResponse>> listarPorEntidad(
            @PathVariable String entidadTipo,
            @PathVariable UUID entidadId
    ) {
        return ResponseEntity.ok(archivoService.listarPorEntidad(entidadTipo, entidadId));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable UUID id) {
        ArchivoAdjunto adjunto = archivoService.obtenerEntidad(id);
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL')")
    public ResponseEntity<Void> eliminarArchivo(@PathVariable UUID id) {
        archivoService.eliminarArchivo(id);
        return ResponseEntity.noContent().build();
    }
}
