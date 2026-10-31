package com.appdomotica.proyectos.dto;

import com.appdomotica.cotizaciones.entity.EstadoExtra;
import com.appdomotica.proyectos.entity.EstadoCambio;
import com.appdomotica.proyectos.entity.EstadoProyecto;
import com.appdomotica.proyectos.entity.TipoLineaCambio;
import com.appdomotica.proyectos.entity.EstadoTarea;
import com.appdomotica.proyectos.entity.EstadoVisita;
import com.appdomotica.usuarios.entity.RolUsuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de proyectos y checklist. Ninguno lleva precios: el instalador los ve (RN-03). */
public final class ProyectoDtos {
    private ProyectoDtos() {
    }

    public record ProyectoResumen(UUID id, EstadoProyecto estado, String clienteNombre, String clienteTelefono,
                                  String clienteDireccion, String cotizacionNumero, OffsetDateTime fechaCreacion,
                                  int tareasTotal, int tareasHechas, boolean retornoPendiente,
                                  OffsetDateTime proximaVisita) {
    }

    /**
     * Ubicación del informe técnico: foto marcada y nota, para que el instalador sepa dónde va cada equipo.
     * Con `checklist`, además sus puntos, las fotos de antes y después de la ubicación y si quedó completada
     * (calculado: todos los puntos marcados, foto de antes y de después, y sus tareas, si tiene, terminadas).
     */
    public record UbicacionProyecto(UUID id, int numero, String nota, UUID fotoId, List<ItemChecklist> checklist,
                                    List<UUID> fotosAntes, List<UUID> fotosDespues, boolean completada) {
    }

    /** Punto de la checklist de una ubicación (copiado del informe al iniciar el proyecto). */
    public record ItemChecklist(UUID id, String texto, boolean marcado, String marcadoPor, OffsetDateTime marcadoEn,
                                Integer visitaNumero) {
    }

    public record MarcarItemRequest(@jakarta.validation.constraints.NotNull(message = "Indica si queda marcado")
                                    Boolean marcado, OffsetDateTime ocurridoEn) {
    }

    public record ItemInstruccion(UUID id, String productoNombre, int cantidad, String descripcion, String accion) {
    }

    public record VersionInstrucciones(UUID id, int numero, String generadaDesde, String creadoPor,
                                       OffsetDateTime fecha, List<ItemInstruccion> items) {
    }

    /** `fotosAntes`/`fotosDespues`: evidencia (ids de archivo). `ubicacionNumero`: 1-based, null si es general. */
    public record Tarea(UUID id, String descripcion, EstadoTarea estado, String notas, Integer cantidad,
                        String productoNombre, UUID ubicacionId, Integer ubicacionNumero, int versionInstrucciones,
                        List<UUID> fotosAntes, List<UUID> fotosDespues, String completadaPor,
                        OffsetDateTime completadaEn, Integer visitaNumero, long version) {
    }

    /** Visita con su propio avance (`tareasHechas`: completadas o "no aplica" en ella). */
    public record Visita(UUID id, int numero, EstadoVisita estado, UUID asignadoAId, String asignadoA,
                         OffsetDateTime fechaHora, int duracionMinutos, String motivo, String motivoRetorno,
                         OffsetDateTime iniciadaEn,
                         String iniciadaPor, OffsetDateTime terminadaEn, int tareasHechas, long version) {
    }

    /** Firma de conformidad: `archivoId` es la imagen PNG. */
    public record Firma(String nombre, OffsetDateTime fecha, UUID archivoId, int visitaNumero, String registradaPor) {
    }

    public record Evento(String tipo, String usuario, String detalle, OffsetDateTime fecha) {
    }

    public record ProyectoDetalle(UUID id, EstadoProyecto estado, String clienteNombre, String clienteTelefono,
                                  String clienteDireccion, String cotizacionNumero, Integer informeVersion,
                                  String notasInforme, OffsetDateTime fechaCreacion, long version,
                                  VersionInstrucciones instrucciones, List<UbicacionProyecto> ubicaciones,
                                  List<Tarea> tareas, List<Evento> eventos, List<Visita> visitas, Firma firma,
                                  boolean reporteDisponible, List<String> bloqueosCierre,
                                  List<Cambio> cambios, List<VersionResumen> versiones, List<ExtraProyecto> extras) {
    }

    // ---------- cambios de instrucción y extras (RF-PRY-03..08) ----------

    /**
     * AGREGAR: producto (con cantidad) o una tarea libre (solo descripción), con ubicación opcional.
     * CAMBIAR_CANTIDAD y QUITAR: sobre una tarea pendiente del checklist vigente.
     */
    public record LineaCambioRequest(@NotNull TipoLineaCambio tipo, UUID tareaId, UUID productoId, UUID ubicacionId,
                                     @Positive Integer cantidad, @Size(max = 300) String descripcion) {
    }

    /** Sin líneas, el cambio agrega una tarea con la descripción. */
    public record ProponerCambioRequest(
            @NotBlank(message = "Describe qué hay que cambiar y por qué") @Size(max = 2000) String descripcion,
            boolean requiereMaterial, List<@Valid LineaCambioRequest> lineas) {
    }

    public record RechazoCambioRequest(@NotNull Long version,
                                       @NotBlank(message = "Indica por qué se rechaza") @Size(max = 2000) String motivo) {
    }

    /** `texto`: la línea ya redactada para mostrarla tal cual. */
    public record LineaCambio(TipoLineaCambio tipo, String texto, UUID tareaId, String productoNombre,
                              Integer ubicacionNumero, Integer cantidad, Integer cantidadAnterior, String descripcion) {
    }

    /** Cambio de instrucciones con quién lo propuso y quién lo resolvió (RF-PRY-07). `fotos`: ids de archivo. */
    public record Cambio(UUID id, EstadoCambio estado, String descripcion, boolean requiereMaterial,
                         String propuestoPor, UUID propuestoPorId, OffsetDateTime propuestoEn, String resueltoPor,
                         OffsetDateTime resueltoEn, String motivoRechazo, int versionAnterior, Integer versionNueva,
                         List<LineaCambio> lineas, List<UUID> fotos, Integer extraNumero, EstadoExtra extraEstado,
                         long version) {
    }

    /** Una versión de las instrucciones y, si vino de un cambio, el motivo y las personas. */
    public record VersionResumen(int numero, String generadaDesde, String creadoPor, OffsetDateTime fecha,
                                 String motivo, String propuestoPor) {
    }

    /** Extra de materiales visto desde el proyecto: sin precios (el instalador lo ve, RN-03). */
    public record ExtraProyecto(int numero, EstadoExtra estado, String motivo, List<ItemExtraProyecto> items) {
    }

    public record ItemExtraProyecto(String productoNombre, int cantidad) {
    }

    /** PENDIENTE (reabrir), COMPLETADA (exige fotos de antes y después) o NO_APLICA (exige nota). */
    public record CambioTareaRequest(@NotNull Long version, @NotNull EstadoTarea estado,
                                     @Size(max = 2000) String notas, OffsetDateTime ocurridoEn) {
    }

    /**
     * `ocurridoEn`: cuándo se hizo en el teléfono (las operaciones hechas sin conexión llegan después).
     * El servidor lo acepta si cae en los últimos 30 días y no está en el futuro; si no, usa la hora actual.
     */
    public record MomentoRequest(OffsetDateTime ocurridoEn) {
    }

    public record RetornoRequest(@NotBlank @Size(max = 2000) String motivo, OffsetDateTime ocurridoEn) {
    }

    /**
     * `duracionMinutos`: 15 a 720 (si falta, 120). `confirmarCruce`: agendar aunque la persona ya tenga otra
     * visita en ese horario (RF-AGE-03); sin él, un cruce responde 409 `CRUCE_AGENDA` y no guarda nada.
     */
    public record AgendarRequest(@NotNull UUID asignadoAId, @NotNull OffsetDateTime fechaHora,
                                 @Size(max = 2000) String motivo,
                                 @Min(15) @Max(720) Integer duracionMinutos, boolean confirmarCruce) {
    }

    /** `duracionMinutos` null conserva la que tenía. */
    public record ReagendarRequest(@NotNull Long version, @NotNull UUID asignadoAId,
                                   @NotNull OffsetDateTime fechaHora,
                                   @Min(15) @Max(720) Integer duracionMinutos, boolean confirmarCruce) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    /** Persona a la que se le puede asignar una visita. */
    public record Persona(UUID id, String nombre, RolUsuario rol) {
    }
}
