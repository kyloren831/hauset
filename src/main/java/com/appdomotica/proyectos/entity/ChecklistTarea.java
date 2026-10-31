package com.appdomotica.proyectos.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_tareas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruccion_version_id", nullable = false)
    private InstruccionVersion instruccionVersion;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    @Builder.Default
    private EstadoTarea estado = EstadoTarea.PENDIENTE;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "orden", nullable = false)
    @Builder.Default
    private Integer orden = 0;

    /** Ubicación del informe técnico donde va (foto marcada y nota); null si es una tarea general. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_id")
    private com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion ubicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private com.appdomotica.catalogo.entity.ProductoCatalogo producto;

    @Column(name = "cantidad")
    private Integer cantidad;

    /** False cuando una versión posterior de las instrucciones la reemplaza o la quita (RN-06). */
    @Column(name = "vigente", nullable = false)
    @Builder.Default
    private Boolean vigente = true;

    @Column(name = "origen_tarea_id")
    private UUID origenTareaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completada_por")
    private com.appdomotica.usuarios.entity.Usuario completadaPor;

    @Column(name = "completada_en")
    private OffsetDateTime completadaEn;

    /** Visita en la que se completó o se marcó "no aplica" (avance por visita, RF-PRY-11). */
    @Column(name = "completada_en_visita_id")
    private UUID completadaEnVisitaId;

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
