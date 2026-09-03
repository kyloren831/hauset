package com.appdomotica.cotizaciones.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cotizacion_extra_items")
public class CotizacionExtraItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extra_id")
    private CotizacionExtra extra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoCatalogo producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    private Integer cantidad;

    @Column(name = "precio_neto")
    private BigDecimal precioNeto;

    @Column(name = "margen_porcentaje")
    private BigDecimal margenPorcentaje;

    @Column(name = "precio_venta")
    private BigDecimal precioVenta;

    private BigDecimal subtotal;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public CotizacionExtra getExtra() { return extra; }
    public void setExtra(CotizacionExtra extra) { this.extra = extra; }
    public ProductoCatalogo getProducto() { return producto; }
    public void setProducto(ProductoCatalogo producto) { this.producto = producto; }
    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioNeto() { return precioNeto; }
    public void setPrecioNeto(BigDecimal precioNeto) { this.precioNeto = precioNeto; }
    public BigDecimal getMargenPorcentaje() { return margenPorcentaje; }
    public void setMargenPorcentaje(BigDecimal margenPorcentaje) { this.margenPorcentaje = margenPorcentaje; }
    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
