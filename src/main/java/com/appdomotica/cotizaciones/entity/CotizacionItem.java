package com.appdomotica.cotizaciones.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fotografía de un ítem al momento de cotizar (RF-COT-04): nada aquí se recalcula
 * desde el catálogo ni desde el historial de precios.
 */
@Getter
@Setter
@Entity
@Table(name = "cotizacion_items")
public class CotizacionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoCatalogo producto;

    /** Nombre del producto tal como se cotizó (el catálogo puede cambiar después). */
    @Column(name = "producto_nombre")
    private String productoNombre;

    /** Producto que este ítem reemplaza (el proveedor no tenía el pedido), para ver la diferencia con el informe. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_original_id")
    private ProductoCatalogo productoOriginal;

    @Column(name = "producto_original_nombre")
    private String productoOriginalNombre;

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
