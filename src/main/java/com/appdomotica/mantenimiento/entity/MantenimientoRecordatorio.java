package com.appdomotica.mantenimiento.entity;

import com.appdomotica.proyectos.entity.Proyecto;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Recordatorio de mantenimiento: uno por proyecto, a 6 meses del cierre (RN-08, RF-MAN-07). Lo crea la
 * tarea diaria; el equipo lo marca como contactado u omitido (RF-MAN-09).
 */
@Entity
@Table(name = "mantenimiento_recordatorios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MantenimientoRecordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    @Builder.Default
    private EstadoRecordatorio estado = EstadoRecordatorio.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contactado_por")
    private Usuario contactadoPor;

    @Column(name = "fecha_contacto")
    private OffsetDateTime fechaContacto;

    @Column(name = "nota", columnDefinition = "TEXT")
    private String nota;

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
