package com.appdomotica.clientes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "leads")
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Origen del contacto (RF-COT-01). Por defecto WhatsApp. */
    private String origen = "WHATSAPP";

    private OffsetDateTime fecha = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoLead estado = EstadoLead.NUEVO;

    @Column(columnDefinition = "TEXT")
    private String notas;
}
