package com.appdomotica.catalogo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalogo_productos")
public class ProductoCatalogo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private CategoriaProducto categoria;

    private String especificacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCatalogo estado = EstadoCatalogo.ACTIVO;

    /** Se puede usar en informes y cotizaciones nuevas. */
    public boolean esActivo() {
        return estado == EstadoCatalogo.ACTIVO;
    }
}
