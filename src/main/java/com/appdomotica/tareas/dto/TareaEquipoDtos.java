package com.appdomotica.tareas.dto;

import com.appdomotica.tareas.entity.EstadoTareaEquipo;
import com.appdomotica.tareas.entity.OrigenTareaEquipo;
import com.appdomotica.tareas.entity.TareaEquipo;
import com.appdomotica.usuarios.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TareaEquipoDtos {
    private TareaEquipoDtos() {
    }

    /** Ligar a lo sumo uno de los tres registros (RF-TAR-04). */
    public record NuevaTareaRequest(
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 4000) String descripcion,
            LocalDate fechaLimite,
            UUID asignadaAId,
            boolean privada,
            UUID leadId,
            UUID cotizacionId,
            UUID proyectoId) {
    }

    public record EditarTareaRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 4000) String descripcion,
            LocalDate fechaLimite,
            UUID asignadaAId,
            boolean privada) {
    }

    public record MoverTareaRequest(@NotNull Long version, @NotNull EstadoTareaEquipo estado) {
    }

    /** A quién se puede asignar una tarea: los dos jefes y la secretaria. */
    public record PersonaResponse(UUID id, String nombre, String rol) {
        public static PersonaResponse de(Usuario u) {
            return new PersonaResponse(u.getId(), u.getNombre(), u.getRol().name());
        }
    }

    /** Registro ligado; `tipo` es LEAD, COTIZACION o PROYECTO. */
    public record RegistroLigado(String tipo, UUID id, String etiqueta) {
    }

    public record TareaResponse(
            UUID id, String titulo, String descripcion, EstadoTareaEquipo estado, boolean privada,
            LocalDate fechaLimite, UUID creadaPorId, String creadaPorNombre, UUID asignadaAId,
            String asignadaANombre, OrigenTareaEquipo origen, String completadaPorNombre,
            OffsetDateTime completadaEn, RegistroLigado ligada, String aviso, OffsetDateTime createdAt,
            Long version) {

        public static TareaResponse de(TareaEquipo t) {
            Usuario asignada = t.getAsignadaA();
            return new TareaResponse(t.getId(), t.getTitulo(), t.getDescripcion(), t.getEstado(), t.isPrivada(),
                    t.getFechaLimite(), t.getCreadaPor().getId(), t.getCreadaPor().getNombre(),
                    asignada != null ? asignada.getId() : null, asignada != null ? asignada.getNombre() : null,
                    t.getOrigen(), t.getCompletadaPor() != null ? t.getCompletadaPor().getNombre() : null,
                    t.getCompletadaEn(), ligada(t), aviso(t), t.getCreatedAt(), t.getVersion());
        }

        private static RegistroLigado ligada(TareaEquipo t) {
            if (t.getCotizacion() != null) {
                var c = t.getCotizacion();
                String numero = c.getNumero() != null ? c.getNumero() : "Cotización en borrador";
                return new RegistroLigado("COTIZACION", c.getId(), numero + " · " + c.getCliente().getNombre());
            }
            if (t.getProyecto() != null) {
                var p = t.getProyecto();
                return new RegistroLigado("PROYECTO", p.getId(),
                        "Proyecto · " + p.getCotizacion().getCliente().getNombre());
            }
            if (t.getLead() != null) {
                return new RegistroLigado("LEAD", t.getLead().getId(), "Lead · " + t.getLead().getCliente().getNombre());
            }
            return null;
        }

        /** Lo que falta para poder cumplir una tarea automática (RF-COT-18). */
        private static String aviso(TareaEquipo t) {
            if (t.getOrigen() == OrigenTareaEquipo.COTIZACION_ACEPTADA && t.getEstado() != EstadoTareaEquipo.HECHA
                    && t.getCotizacion() != null && t.getCotizacion().getInforme() == null) {
                return "Falta el informe técnico: el jefe de sistemas lo levanta desde la cotización";
            }
            return null;
        }
    }
}
