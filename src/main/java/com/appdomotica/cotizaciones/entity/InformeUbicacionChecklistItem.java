package com.appdomotica.cotizaciones.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Un punto de la checklist de una ubicación del informe técnico (texto y orden). */
@Getter
@Setter
@Entity
@Table(name = "informe_ubicacion_checklist")
public class InformeUbicacionChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private InformeTecnicoUbicacion ubicacion;

    @Column(nullable = false, length = 300)
    private String texto;

    @Column(nullable = false)
    private Integer orden;
}
