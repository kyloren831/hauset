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
@Table(name = "proveedores")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    /** Categorías que atiende, separadas por coma en minúscula (p. ej. "seguridad, control_acceso"). */
    private String categoriasAsociadas;
    private String contacto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCatalogo estado = EstadoCatalogo.ACTIVO;

    /** Se puede usar en cotizaciones nuevas. */
    public boolean esActivo() {
        return estado == EstadoCatalogo.ACTIVO;
    }
}
