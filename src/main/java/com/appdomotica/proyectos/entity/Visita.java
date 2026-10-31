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

/** Ida del equipo al sitio (RF-PRY-11). Cada una lleva su propio avance (tareas completadas en ella). */
@Entity
@Table(name = "visitas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Visita {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "numero_visita", nullable = false)
    @Builder.Default
    private Integer numeroVisita = 1;

    /** Null mientras un retorno espera agenda. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_a")
    private Usuario asignadoA;

    /** Null si el equipo la inició en sitio sin agenda. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendado_por")
    private Usuario agendadoPor;

    @Column(name = "fecha_hora")
    private OffsetDateTime fechaHora;

    /** Cuánto se espera que dure; con esto se detectan cruces (RF-AGE-03). */
    @Column(name = "duracion_minutos", nullable = false)
    @Builder.Default
    private Integer duracionMinutos = 120;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    @Builder.Default
    private EstadoVisita estado = EstadoVisita.AGENDADA;

    /** Para qué es la visita (p. ej. por qué hay que volver). */
    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    /** Por qué, al terminar esta visita, hace falta volver (RF-PRY-12). */
    @Column(name = "motivo_retorno", columnDefinition = "TEXT")
    private String motivoRetorno;

    @Column(name = "iniciada_en")
    private OffsetDateTime iniciadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iniciada_por")
    private Usuario iniciadaPor;

    @Column(name = "terminada_en")
    private OffsetDateTime terminadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminada_por")
    private Usuario terminadaPor;

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
