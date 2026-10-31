package com.appdomotica.proyectos.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Firma de conformidad del cliente al cerrar (RF-PRY-13). Solo inserción (trigger en la base). */
@Entity
@Table(name = "firmas_conformidad")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmaConformidad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visita_id", nullable = false, unique = true, updatable = false)
    private Visita visita;

    @Column(name = "firmado_por_cliente", nullable = false, length = 150, updatable = false)
    private String firmadoPorCliente;

    /** Imagen PNG de la firma en archivos_adjuntos. */
    @Column(name = "archivo_id", updatable = false)
    private UUID archivoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por", updatable = false)
    private Usuario registradaPor;

    @Column(name = "fecha", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
