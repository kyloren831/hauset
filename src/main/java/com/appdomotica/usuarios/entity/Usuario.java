package com.appdomotica.usuarios.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private RolUsuario rol;

    private String telefono;
    private String passwordHash;

    private Boolean activo = true;
}
