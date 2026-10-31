package com.appdomotica.multimedia.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "archivos_adjuntos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entidad_tipo", nullable = false, length = 50)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false)
    private UUID entidadId;

    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria;

    @Column(name = "storage_provider", nullable = false, length = 30)
    @Builder.Default
    private String storageProvider = "DISCO_LOCAL";

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "nombre_original", nullable = false)
    private String nombreOriginal;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "ancho_px")
    private Integer anchoPx;

    @Column(name = "alto_px")
    private Integer altoPx;

    @Column(name = "thumbnail_storage_key", length = 500)
    private String thumbnailStorageKey;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "DISPONIBLE";

    @Column(name = "notas")
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por")
    private Usuario subidoPor;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
