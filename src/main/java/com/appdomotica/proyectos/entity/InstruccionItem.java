package com.appdomotica.proyectos.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Contenido de una versión de instrucciones. Inmutable una vez creada (trigger en la base). */
@Getter
@Setter
@Entity
@Table(name = "instruccion_items")
public class InstruccionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instruccion_version_id", nullable = false)
    private InstruccionVersion instruccionVersion;

    @Column(nullable = false)
    private Integer orden = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoCatalogo producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(name = "origen_item_id")
    private UUID origenItemId;

    @Column(nullable = false, length = 20)
    private String accion = "AGREGADO";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
