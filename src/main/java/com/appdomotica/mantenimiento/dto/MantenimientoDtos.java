package com.appdomotica.mantenimiento.dto;

import com.appdomotica.mantenimiento.entity.EstadoRecordatorio;
import com.appdomotica.mantenimiento.entity.TipoCredencial;
import com.appdomotica.proyectos.entity.EstadoProyecto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Equipos instalados, credenciales y recordatorios de mantenimiento (RF-MAN-01..09). */
public final class MantenimientoDtos {
    private MantenimientoDtos() {
    }

    // ---------- equipos y credenciales ----------

    /** `version` solo al editar. `productoId` es opcional (equipo del cliente). */
    public record EquipoRequest(
            @NotBlank(message = "Escribe un nombre para reconocer el equipo") @Size(max = 200) String nombreReferencia,
            UUID productoId,
            @Size(max = 100) String numeroSerie,
            @Size(max = 50) String direccionMac,
            Long version) {
    }

    /** Al editar, `contrasena` vacía o null deja la que estaba. */
    public record CredencialRequest(@NotNull(message = "Elige el tipo") TipoCredencial tipo,
                                    @Size(max = 150) String usuario,
                                    @Size(max = 200) String contrasena,
                                    @Size(max = 2000) String notas,
                                    Long version) {
        /** Spring web en DEBUG imprime los cuerpos: la contraseña nunca va al log. */
        @Override
        public String toString() {
            return "CredencialRequest[tipo=" + tipo + ", usuario=" + usuario + ", contrasena=***, version=" + version + "]";
        }
    }

    /** Nunca trae la contraseña: para verla hay que pedirla y queda registrado (RF-MAN-06). */
    public record Credencial(UUID id, TipoCredencial tipo, String usuario, String notas, String actualizadaPor,
                             OffsetDateTime fechaActualizacion, long consultas, long version) {
    }

    public record CredencialRevelada(UUID id, String contrasena) {
        @Override
        public String toString() {
            return "CredencialRevelada[id=" + id + ", contrasena=***]";
        }
    }

    public record Acceso(String persona, OffsetDateTime fecha) {
    }

    public record Equipo(UUID id, String nombreReferencia, UUID productoId, String productoNombre,
                         String numeroSerie, String direccionMac, LocalDate fechaInstalacion,
                         List<Credencial> credenciales, long version) {
    }

    public record ProductoOpcion(UUID id, String nombre) {
    }

    /** Equipos de un proyecto, con los productos de su checklist para elegir al registrar uno. */
    public record EquiposProyecto(UUID proyectoId, String clienteNombre, String clienteDireccion,
                                  EstadoProyecto estado, LocalDate fechaCierre, List<Equipo> equipos,
                                  List<ProductoOpcion> productos) {
    }

    /** Para buscar las credenciales de un cliente: un renglón por proyecto con equipos. */
    public record ProyectoConEquipos(UUID proyectoId, String clienteNombre, String clienteDireccion,
                                     EstadoProyecto estado, LocalDate fechaCierre, List<String> equipos,
                                     int credenciales) {
    }

    // ---------- recordatorios ----------

    public record ResolverRecordatorioRequest(@NotNull Long version, @Size(max = 1000) String nota) {
    }

    /** `mensaje`: texto prellenado para el cliente; el envío por WhatsApp es manual (RF-MAN-08). */
    public record Recordatorio(UUID id, UUID proyectoId, String clienteNombre, String clienteTelefono,
                               String clienteDireccion, LocalDate fechaInstalacion, LocalDate fechaProgramada,
                               EstadoRecordatorio estado, String resueltoPor, OffsetDateTime fechaResuelto,
                               String nota, List<String> equipos, String mensaje, long version) {
    }

    /** Proyecto cerrado cuyo recordatorio llega pronto (aún no existe). */
    public record Proximo(UUID proyectoId, String clienteNombre, LocalDate fechaInstalacion,
                          LocalDate fechaProgramada) {
    }

    public record Recordatorios(List<Recordatorio> pendientes, List<Proximo> proximos,
                                List<Recordatorio> recientes) {
    }
}
