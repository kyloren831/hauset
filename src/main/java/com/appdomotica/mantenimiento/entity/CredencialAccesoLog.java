package com.appdomotica.mantenimiento.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Quién vio una contraseña y cuándo (RF-MAN-06). Solo inserción: la base bloquea cambios y borrados. */
@Entity
@Table(name = "credenciales_accesos_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredencialAccesoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credencial_id", nullable = false, updatable = false)
    private CredencialEquipo credencial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultado_por", nullable = false, updatable = false)
    private Usuario consultadoPor;

    @Column(name = "fecha", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fecha = OffsetDateTime.now();
}
