package com.appdomotica.proyectos.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "instrucciones_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstruccionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    @Column(name = "generada_desde", nullable = false, length = 50)
    private String generadaDesde; // COTIZACION, CAMBIO_SITIO

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha", nullable = false)
    @Builder.Default
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 1L;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
