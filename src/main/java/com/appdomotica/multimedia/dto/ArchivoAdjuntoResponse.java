package com.appdomotica.multimedia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoAdjuntoResponse {
    private UUID id;
    private String entidadTipo;
    private UUID entidadId;
    private String categoria;
    private String storageKey;
    private String nombreOriginal;
    private String mimeType;
    private Long tamanoBytes;
    private String checksumSha256;
    private String urlDescarga;
    private String estado;
    private String notas;
    private OffsetDateTime createdAt;
}
