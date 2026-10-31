package com.appdomotica.mantenimiento.entity;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.proyectos.entity.Proyecto;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Equipo instalado en un proyecto (RF-MAN-01). La fecha de instalación queda vacía hasta que el proyecto
 * se cierra (RF-MAN-02). El producto es opcional: también se registran equipos del cliente (su router).
 */
@Entity
@Table(name = "equipos_instalados")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipoInstalado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoCatalogo producto;

    @Column(name = "nombre_referencia", nullable = false, length = 200)
    private String nombreReferencia;

    @Column(name = "numero_serie", length = 100)
    private String numeroSerie;

    /** Normalizada: AA:BB:CC:DD:EE:FF. */
    @Column(name = "direccion_mac", length = 50)
    private String direccionMac;

    @Column(name = "fecha_instalacion")
    private LocalDate fechaInstalacion;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
