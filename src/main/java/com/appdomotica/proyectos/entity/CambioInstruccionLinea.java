package com.appdomotica.proyectos.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Qué cambia en el checklist con un cambio de instrucciones. Solo se inserta (trigger en la base). */
@Getter
@Setter
@Entity
@Table(name = "cambio_instruccion_lineas")
public class CambioInstruccionLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cambio_id", nullable = false, updatable = false)
    private CambioInstruccion cambio;

    @Column(nullable = false, updatable = false)
    private Integer orden = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoLineaCambio tipo;

    /** Tarea que cambia de cantidad o se quita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarea_id", updatable = false)
    private ChecklistTarea tarea;

    /** Producto que se agrega (null en una tarea libre, que lleva solo descripción). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", updatable = false)
    private ProductoCatalogo producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_id", updatable = false)
    private InformeTecnicoUbicacion ubicacion;

    /** Cantidad nueva (AGREGAR, CAMBIAR_CANTIDAD). */
    @Column(updatable = false)
    private Integer cantidad;

    @Column(length = 300, updatable = false)
    private String descripcion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
