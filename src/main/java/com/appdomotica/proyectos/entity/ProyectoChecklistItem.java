package com.appdomotica.proyectos.entity;

import com.appdomotica.cotizaciones.entity.InformeTecnicoUbicacion;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ítem de la checklist de una ubicación, copiado del informe al iniciar el proyecto. El técnico solo lo
 * marca o desmarca; la ubicación queda completada cuando todos sus ítems están marcados (se calcula).
 */
@Getter
@Setter
@Entity
@Table(name = "proyecto_checklist_items")
public class ProyectoChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private InformeTecnicoUbicacion ubicacion;

    @Column(name = "origen_item_id")
    private UUID origenItemId;

    @Column(nullable = false, length = 300)
    private String texto;

    @Column(nullable = false)
    private Integer orden;

    @Column(nullable = false)
    private boolean marcado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marcado_por")
    private Usuario marcadoPor;

    @Column(name = "marcado_en")
    private OffsetDateTime marcadoEn;

    @Column(name = "marcado_en_visita_id")
    private UUID marcadoEnVisitaId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
