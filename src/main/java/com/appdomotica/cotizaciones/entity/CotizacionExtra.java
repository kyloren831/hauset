package com.appdomotica.cotizaciones.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extra de materiales: adenda a una cotización aceptada (RF-COT-10). No genera otro documento para el
 * cliente; suma al total facturado solo cuando un jefe lo confirma (RF-COT-11, RN-05).
 */
@Getter
@Setter
@Entity
@Table(name = "cotizacion_extras")
public class CotizacionExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    /** 1, 2, 3… dentro de la cotización. */
    @Column(nullable = false)
    private Integer numero;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoExtra estado = EstadoExtra.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenExtra origen = OrigenExtra.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margen_definido_por")
    private Usuario margenDefinidoPor;

    @Column(name = "margen_definido_en")
    private OffsetDateTime margenDefinidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por")
    private Usuario confirmadoPor;

    /** Fecha de confirmación. */
    private OffsetDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descartado_por")
    private Usuario descartadoPor;

    @Column(name = "descartado_en")
    private OffsetDateTime descartadoEn;

    @Column(name = "motivo_descarte", columnDefinition = "TEXT")
    private String motivoDescarte;

    @Column(name = "iva_porcentaje")
    private BigDecimal ivaPorcentaje;

    /** Total sin IVA. */
    @Column(name = "total_extra", nullable = false)
    private BigDecimal totalExtra = BigDecimal.ZERO;

    @Column(name = "monto_iva", nullable = false)
    private BigDecimal montoIva = BigDecimal.ZERO;

    @Column(name = "total_con_iva", nullable = false)
    private BigDecimal totalConIva = BigDecimal.ZERO;

    @OneToMany(mappedBy = "extra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<CotizacionExtraItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;
}
