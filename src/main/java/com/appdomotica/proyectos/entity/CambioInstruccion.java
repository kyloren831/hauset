package com.appdomotica.proyectos.entity;

import com.appdomotica.cotizaciones.entity.CotizacionExtra;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cambios_instruccion")
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambioInstruccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruccion_anterior_id", nullable = false)
    private InstruccionVersion instruccionAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instruccion_nueva_id")
    private InstruccionVersion instruccionNueva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propuesto_por", nullable = false)
    private Usuario propuestoPor;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "requiere_material", nullable = false)
    @Builder.Default
    private Boolean requiereMaterial = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 50)
    @Builder.Default
    private EstadoCambio estado = EstadoCambio.PENDIENTE_CONFIRMACION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por")
    private Usuario confirmadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extra_id")
    private CotizacionExtra extra;

    @Column(name = "fecha_propuesta", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaPropuesta = OffsetDateTime.now();

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @OneToMany(mappedBy = "cambio", cascade = CascadeType.PERSIST)
    @OrderBy("orden ASC")
    @Builder.Default
    private java.util.List<CambioInstruccionLinea> lineas = new java.util.ArrayList<>();

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
