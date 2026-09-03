package com.appdomotica.cotizaciones.entity;

import com.appdomotica.clientes.entity.Cliente;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cotizaciones")
public class Cotizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "informe_id")
    private InformeTecnico informe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "armada_por")
    private Usuario armadaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margen_definido_por")
    private Usuario margenDefinidoPor;

    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private EstadoCotizacion estado;

    private BigDecimal total;

    @Column(name = "pdf_url")
    private String pdfUrl;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public InformeTecnico getInforme() { return informe; }
    public void setInforme(InformeTecnico informe) { this.informe = informe; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Usuario getArmadaPor() { return armadaPor; }
    public void setArmadaPor(Usuario armadaPor) { this.armadaPor = armadaPor; }
    public Usuario getMargenDefinidoPor() { return margenDefinidoPor; }
    public void setMargenDefinidoPor(Usuario margenDefinidoPor) { this.margenDefinidoPor = margenDefinidoPor; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public EstadoCotizacion getEstado() { return estado; }
    public void setEstado(EstadoCotizacion estado) { this.estado = estado; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
}
