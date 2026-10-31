package com.appdomotica.proyectos.entity;

import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Bitácora del proyecto: solo se inserta (trigger en la base), nunca se edita ni se borra. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "proyecto_eventos")
public class ProyectoEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false, updatable = false)
    private Proyecto proyecto;

    @Column(nullable = false, length = 40, updatable = false)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "usuario_nombre", length = 150, updatable = false)
    private String usuarioNombre;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String detalle;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    public ProyectoEvento(Proyecto proyecto, String tipo, Usuario usuario, String detalle) {
        this.proyecto = proyecto;
        this.tipo = tipo;
        this.usuario = usuario;
        this.usuarioNombre = usuario != null ? usuario.getNombre() : null;
        this.detalle = detalle;
    }
}
