package com.appdomotica.cotizaciones.entity;

import com.appdomotica.clientes.entity.Lead;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "informes_tecnicos")
public class InformeTecnico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingeniero_id")
    private Usuario ingeniero;

    @Column(columnDefinition = "TEXT")
    private String notas;

    private LocalDateTime fecha;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Lead getLead() { return lead; }
    public void setLead(Lead lead) { this.lead = lead; }
    public Usuario getIngeniero() { return ingeniero; }
    public void setIngeniero(Usuario ingeniero) { this.ingeniero = ingeniero; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
