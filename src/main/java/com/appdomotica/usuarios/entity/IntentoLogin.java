package com.appdomotica.usuarios.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "intentos_login")
public class IntentoLogin {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String telefono;

    private String ip;

    @Column(nullable = false)
    private boolean exitoso;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    public IntentoLogin(String telefono, String ip, boolean exitoso) {
        this.telefono = telefono;
        this.ip = ip;
        this.exitoso = exitoso;
    }
}
