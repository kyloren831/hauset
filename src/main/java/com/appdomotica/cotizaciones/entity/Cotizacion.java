package com.appdomotica.cotizaciones.entity;

import com.appdomotica.clientes.entity.Cliente;
import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "cotizaciones")
public class Cotizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Número legible (COT-2026-0001); se asigna al enviar. */
    private String numero;

    /** Opcional: una cotización en blanco no tiene informe y puede recibirlo después (RF-COT-12, RF-COT-16). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "informe_id")
    private InformeTecnico informe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "armada_por", nullable = false)
    private Usuario armadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margen_definido_por")
    private Usuario margenDefinidoPor;

    @Column(name = "margen_definido_en")
    private OffsetDateTime margenDefinidoEn;

    private OffsetDateTime fecha = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCotizacion estado = EstadoCotizacion.BORRADOR;

    /** Porcentaje de IVA fotografiado en esta cotización. */
    @Column(name = "iva_porcentaje", nullable = false)
    private BigDecimal ivaPorcentaje;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iva_definido_por")
    private Usuario ivaDefinidoPor;

    @Column(name = "total_sin_iva", nullable = false)
    private BigDecimal totalSinIva = BigDecimal.ZERO;

    @Column(name = "monto_iva", nullable = false)
    private BigDecimal montoIva = BigDecimal.ZERO;

    @Column(name = "total_con_iva", nullable = false)
    private BigDecimal totalConIva = BigDecimal.ZERO;

    @Column(name = "validez_dias", nullable = false)
    private Integer validezDias = 15;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_respuesta")
    private OffsetDateTime fechaRespuesta;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<CotizacionItem> items = new ArrayList<>();

    /** Extras de materiales (RF-COT-10); solo se leen desde aquí. */
    @OneToMany(mappedBy = "cotizacion")
    @OrderBy("numero ASC")
    private List<CotizacionExtra> extras = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Veces que se envió al cliente (0 = nunca). Un reenvío tras un rechazo es la revisión 2, etc. */
    @Column(nullable = false)
    private Integer revision = 0;

    @Version
    private Long version;
}
