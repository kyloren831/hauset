package com.appdomotica.cotizaciones.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cotizacion_extras")
public class CotizacionExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotizacion_id")
    private Cotizacion cotizacion;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Enumerated(EnumType.STRING)
    private EstadoExtra estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por")
    private Usuario confirmadoPor;

    private LocalDateTime fecha;

    @Column(name = "total_extra")
    private BigDecimal totalExtra;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Cotizacion getCotizacion() { return cotizacion; }
    public void setCotizacion(Cotizacion cotizacion) { this.cotizacion = cotizacion; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public EstadoExtra getEstado() { return estado; }
    public void setEstado(EstadoExtra estado) { this.estado = estado; }
    public Usuario getConfirmadoPor() { return confirmadoPor; }
    public void setConfirmadoPor(Usuario confirmadoPor) { this.confirmadoPor = confirmadoPor; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public BigDecimal getTotalExtra() { return totalExtra; }
    public void setTotalExtra(BigDecimal totalExtra) { this.totalExtra = totalExtra; }
}
