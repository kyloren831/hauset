package com.appdomotica.cotizaciones.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Ítem de un extra: la misma fotografía de precio que un ítem de cotización (RF-COT-04). */
@Getter
@Setter
@Entity
@Table(name = "cotizacion_extra_items")
public class CotizacionExtraItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extra_id", nullable = false)
    private CotizacionExtra extra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoCatalogo producto;

    @Column(name = "producto_nombre")
    private String productoNombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(name = "precio_neto")
    private BigDecimal precioNeto;

    @Column(name = "margen_porcentaje")
    private BigDecimal margenPorcentaje;

    /** Precio unitario de venta sin IVA. */
    @Column(name = "precio_venta")
    private BigDecimal precioVenta;

    /** Subtotal sin IVA. */
    private BigDecimal subtotal;

    @Column(name = "monto_iva")
    private BigDecimal montoIva;

    @Column(name = "subtotal_con_iva")
    private BigDecimal subtotalConIva;
}
