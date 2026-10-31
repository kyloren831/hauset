package com.appdomotica.cotizaciones.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "informe_tecnico_items")
public class InformeTecnicoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "informe_id", nullable = false)
    private InformeTecnico informe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private InformeTecnicoUbicacion ubicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoCatalogo producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private Integer orden = 0;
}
