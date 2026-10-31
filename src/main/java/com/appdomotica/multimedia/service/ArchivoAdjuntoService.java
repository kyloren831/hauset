package com.appdomotica.multimedia.service;

import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.multimedia.dto.ArchivoAdjuntoResponse;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchivoAdjuntoService {

    private final ArchivoAdjuntoRepository archivoRepository;
    private final StorageService storageService;

    @Transactional
    public ArchivoAdjuntoResponse subirArchivo(
            MultipartFile file,
            String entidadTipo,
            UUID entidadId,
            String categoria,
            String notas,
            Usuario subidoPor
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo a subir no puede estar vacío");
        }

        try {
            byte[] bytes = file.getBytes();
            String checksum = calcularSha256(bytes);

            // Idempotencia / Deduplicación: si el archivo idéntico ya fue subido para esta entidad y categoría, devolverlo directamente
            var existente = archivoRepository.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(
                    entidadTipo.toUpperCase(), entidadId, categoria.toUpperCase()
            ).stream()
             .filter(a -> a.getChecksumSha256().equals(checksum))
             .findFirst();

            if (existente.isPresent()) {
                log.info("Archivo idéntico ya vinculado a entidad {} y categoría {} (idempotencia)", entidadId, categoria);
                return toResponse(existente.get());
            }

            LocalDate hoy = LocalDate.now();
            String subpath = String.format("%s/%d/%02d", entidadTipo.toLowerCase(), hoy.getYear(), hoy.getMonthValue());
            String extension = extraerExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            String storageKey = storageService.store(subpath, filename, bytes);

            ArchivoAdjunto adjunto = ArchivoAdjunto.builder()
                    .entidadTipo(entidadTipo.toUpperCase())
                    .entidadId(entidadId)
                    .categoria(categoria.toUpperCase())
                    .storageProvider("DISCO_LOCAL")
                    .storageKey(storageKey)
                    .nombreOriginal(file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo")
                    .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .tamanoBytes(file.getSize())
                    .checksumSha256(checksum)
                    .estado("DISPONIBLE")
                    .notas(notas)
                    .subidoPor(subidoPor)
                    .build();

            ArchivoAdjunto guardado = archivoRepository.save(adjunto);
            return toResponse(guardado);
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el archivo subido", e);
        }
    }

    /**
     * Guarda un archivo generado por el propio sistema (por ejemplo el PDF de una cotización).
     */
    @Transactional
    public ArchivoAdjunto guardarGenerado(byte[] bytes, String nombre, String mimeType, String entidadTipo,
                                          UUID entidadId, String categoria, Usuario subidoPor) {
        LocalDate hoy = LocalDate.now();
        String subpath = String.format("%s/%d/%02d", entidadTipo.toLowerCase(), hoy.getYear(), hoy.getMonthValue());
        String storageKey = storageService.store(subpath, UUID.randomUUID() + extraerExtension(nombre), bytes);
        ArchivoAdjunto adjunto = ArchivoAdjunto.builder()
                .entidadTipo(entidadTipo.toUpperCase())
                .entidadId(entidadId)
                .categoria(categoria.toUpperCase())
                .storageProvider("DISCO_LOCAL")
                .storageKey(storageKey)
                .nombreOriginal(nombre)
                .mimeType(mimeType)
                .tamanoBytes((long) bytes.length)
                .checksumSha256(calcularSha256(bytes))
                .estado("DISPONIBLE")
                .subidoPor(subidoPor)
                .build();
        return archivoRepository.save(adjunto);
    }

    /** Último archivo vigente de una entidad y categoría, si existe. */
    @Transactional(readOnly = true)
    public java.util.Optional<ArchivoAdjunto> ultimo(String entidadTipo, UUID entidadId, String categoria) {
        return archivoRepository.findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(
                        entidadTipo.toUpperCase(), entidadId, categoria.toUpperCase())
                .stream()
                .max(java.util.Comparator.comparing(ArchivoAdjunto::getCreatedAt));
    }

    @Transactional(readOnly = true)
    public List<ArchivoAdjuntoResponse> listarPorEntidad(String entidadTipo, UUID entidadId) {
        return archivoRepository.findByEntidadTipoAndEntidadIdAndDeletedAtIsNull(
                entidadTipo.toUpperCase(), entidadId
        ).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ArchivoAdjunto obtenerEntidad(UUID id) {
        return archivoRepository.findById(id)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Archivo adjunto no encontrado con ID: " + id));
    }

    public Resource cargarContenido(ArchivoAdjunto archivo) {
        return storageService.loadAsResource(archivo.getStorageKey());
    }

    @Transactional
    public void eliminarArchivo(UUID id) {
        ArchivoAdjunto adjunto = obtenerEntidad(id);
        adjunto.setDeletedAt(OffsetDateTime.now());
        archivoRepository.save(adjunto);
        log.info("Archivo adjunto marcado como eliminado (soft delete): {}", id);
    }

    private String calcularSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private String extraerExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return ".bin";
    }

    public ArchivoAdjuntoResponse toResponse(ArchivoAdjunto adjunto) {
        return ArchivoAdjuntoResponse.builder()
                .id(adjunto.getId())
                .entidadTipo(adjunto.getEntidadTipo())
                .entidadId(adjunto.getEntidadId())
                .categoria(adjunto.getCategoria())
                .storageKey(adjunto.getStorageKey())
                .nombreOriginal(adjunto.getNombreOriginal())
                .mimeType(adjunto.getMimeType())
                .tamanoBytes(adjunto.getTamanoBytes())
                .checksumSha256(adjunto.getChecksumSha256())
                .urlDescarga("/api/v1/archivos/" + adjunto.getId() + "/descargar")
                .estado(adjunto.getEstado())
                .notas(adjunto.getNotas())
                .createdAt(adjunto.getCreatedAt())
                .build();
    }
}
