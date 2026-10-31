package com.appdomotica.cotizaciones.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un punto de la instalación: foto marcada (archivo adjunto), nota y los productos que van ahí. */
@Getter
@Setter
@Entity
@Table(name = "informe_tecnico_ubicaciones")
public class InformeTecnicoUbicacion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "informe_id", nullable = false)
    private InformeTecnico informe;

    @Column(columnDefinition = "TEXT")
    private String nota;

    @Column(nullable = false)
    private Integer orden = 0;

    @OneToMany(mappedBy = "ubicacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<InformeTecnicoItem> items = new ArrayList<>();
}
