package com.appdomotica.cotizaciones.entity;

import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lo que levanta el ingeniero en sistemas tras la visita, sin precios (RF-COT-02): por cada ubicación,
 * una foto marcada, una nota y los productos con su cantidad.
 */
@Getter
@Setter
@Entity
@Table(name = "informes_tecnicos")
public class InformeTecnico {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingeniero_id", nullable = false)
    private Usuario ingeniero;

    /** Común a todas las versiones del mismo informe. */
    @Column(name = "grupo_id", nullable = false)
    private UUID grupoId;

    /** v.1, v.2, ... dentro del grupo. */
    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion = 1;

    @Column(columnDefinition = "TEXT")
    private String notas;

    /** Levantado para una cotización que ya existía: admite fotos mientras no se inicie el proyecto. */
    @Column(name = "desde_cotizacion", nullable = false)
    private boolean desdeCotizacion;

    private OffsetDateTime fecha = OffsetDateTime.now();

    @OneToMany(mappedBy = "informe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<InformeTecnicoUbicacion> ubicaciones = new ArrayList<>();

    /** Todos los productos del informe, ubicación por ubicación. */
    public List<InformeTecnicoItem> todosLosItems() {
        return ubicaciones.stream().flatMap(u -> u.getItems().stream()).toList();
    }
}
