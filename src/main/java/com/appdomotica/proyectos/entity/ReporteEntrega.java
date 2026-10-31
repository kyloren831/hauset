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

/** Reporte final de entrega (RF-PRY-15): el PDF generado al cerrar. Solo inserción. */
@Entity
@Table(name = "reportes_entrega")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false, unique = true, updatable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generado_por", nullable = false, updatable = false)
    private Usuario generadoPor;

    @Column(name = "archivo_id", updatable = false)
    private UUID archivoId;

    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaGeneracion = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
