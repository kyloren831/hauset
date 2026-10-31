package com.appdomotica.tareas.entity;

import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.proyectos.entity.Proyecto;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tarea de la oficina (SRS 3.8): la trabajan los dos jefes y la secretaria. No confundir con
 * las tareas del checklist de un proyecto, que hacen los instaladores.
 */
@Getter
@Setter
@Entity
@Table(name = "tareas_equipo")
public class TareaEquipo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTareaEquipo estado = EstadoTareaEquipo.POR_HACER;

    /** Solo la ve quien la creó (RN-12). */
    @Column(nullable = false)
    private boolean privada;

    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creada_por", nullable = false)
    private Usuario creadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignada_a")
    private Usuario asignadaA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotizacion_id")
    private Cotizacion cotizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrigenTareaEquipo origen = OrigenTareaEquipo.MANUAL;

    /** Id del cambio o del extra que originó una tarea automática (para cerrarla al resolverlo). */
    @Column(name = "origen_ref")
    private UUID origenRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completada_por")
    private Usuario completadaPor;

    @Column(name = "completada_en")
    private OffsetDateTime completadaEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private Long version = 1L;
}
