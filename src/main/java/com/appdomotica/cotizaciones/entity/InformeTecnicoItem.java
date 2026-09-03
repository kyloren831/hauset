package com.appdomotica.cotizaciones.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import jakarta.persistence.*;

@Entity
@Table(name = "informe_tecnico_items")
public class InformeTecnicoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "informe_id")
    private InformeTecnico informe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoCatalogo producto;

    private Integer cantidad;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public InformeTecnico getInforme() { return informe; }
    public void setInforme(InformeTecnico informe) { this.informe = informe; }
    public ProductoCatalogo getProducto() { return producto; }
    public void setProducto(ProductoCatalogo producto) { this.producto = producto; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
}
