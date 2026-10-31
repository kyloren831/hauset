package com.appdomotica.mantenimiento.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Credencial de un equipo (RF-MAN-03). La contraseña va cifrada con AES-256-GCM (RF-MAN-04) y el id es
 * parte del cifrado (AAD), así que se asigna antes de guardar. Nunca se borra: se da de baja.
 */
@Entity
@Table(name = "credenciales_equipo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredencialEquipo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id", nullable = false)
    private EquipoInstalado equipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 50)
    private TipoCredencial tipo;

    @Column(name = "usuario", length = 150)
    private String usuario;

    @Column(name = "contrasena_cifrada", nullable = false, length = 500)
    private String contrasenaCifrada;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por", nullable = false)
    private Usuario actualizadoPor;

    @Column(name = "fecha_actualizacion", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaActualizacion = OffsetDateTime.now();

    /** Null al crearla: así Spring sabe que es nueva aunque el id ya venga puesto. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
