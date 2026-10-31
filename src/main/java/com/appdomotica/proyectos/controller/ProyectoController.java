package com.appdomotica.proyectos.controller;

import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.proyectos.dto.ProyectoDtos.*;
import com.appdomotica.proyectos.service.CierreProyectoService;
import com.appdomotica.proyectos.service.ProyectoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Proyectos y checklist. Consultan jefes, ingeniero industrial, instalador y secretaria; el checklist
 * lo trabajan el instalador y los jefes. Ninguna respuesta incluye precios (RN-03).
 * Visitas: el equipo de campo las inicia, termina o marca "requiere retorno"; solo los jefes y el
 * ingeniero industrial las agendan (RF-AGE-02, RN-09). Cerrar exige la firma del cliente (RN-07).
 */
@RestController
@RequestMapping("/api/proyectos")
@RequiredArgsConstructor
public class ProyectoController {
    private static final String LECTORES =
            "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL', 'INSTALADOR', 'SECRETARIA')";
    private static final String CAMPO = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INSTALADOR')";
    private static final String AGENDA = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'INGENIERO_INDUSTRIAL')";

    private final ProyectoService service;
    private final CierreProyectoService cierre;
    private final com.appdomotica.proyectos.service.CambioInstruccionService cambios;
    private static final String JEFES = "hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')";

    @GetMapping
    @PreAuthorize(LECTORES)
    public List<ProyectoResumen> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize(LECTORES)
    public ProyectoDetalle obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    /** `momento`: ANTES o DESPUES. */
    @PostMapping(value = "/tareas/{tareaId}/fotos/{momento}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAMPO)
    public ProyectoDetalle subirEvidencia(@PathVariable UUID tareaId, @PathVariable String momento,
                                          @RequestParam("file") MultipartFile file,
                                          @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.subirEvidencia(tareaId, momento, file, usuario.getUsuario());
    }

    @DeleteMapping("/tareas/{tareaId}/fotos/{fotoId}")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle eliminarEvidencia(@PathVariable UUID tareaId, @PathVariable UUID fotoId) {
        return service.eliminarEvidencia(tareaId, fotoId);
    }

    @PutMapping("/tareas/{tareaId}/estado")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle cambiarEstado(@PathVariable UUID tareaId, @Valid @RequestBody CambioTareaRequest req,
                                         @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.cambiarEstadoTarea(tareaId, req.version(), req.estado(), req.notas(), req.ocurridoEn(),
                usuario.getUsuario());
    }

    // ---------- visitas ----------

    @PostMapping("/{id}/visitas/iniciar")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle iniciarVisita(@PathVariable UUID id, @RequestBody(required = false) MomentoRequest req,
                                         @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.iniciarVisita(id, req != null ? req.ocurridoEn() : null, usuario.getUsuario());
    }

    @PostMapping("/{id}/visitas/terminar")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle terminarVisita(@PathVariable UUID id, @RequestBody(required = false) MomentoRequest req,
                                          @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.terminarVisita(id, req != null ? req.ocurridoEn() : null, usuario.getUsuario());
    }

    @PostMapping("/{id}/retorno")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle requiereRetorno(@PathVariable UUID id, @Valid @RequestBody RetornoRequest req,
                                           @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.requiereRetorno(id, req.motivo(), req.ocurridoEn(), usuario.getUsuario());
    }

    @PostMapping("/{id}/visitas")
    @PreAuthorize(AGENDA)
    public ProyectoDetalle agendar(@PathVariable UUID id, @Valid @RequestBody AgendarRequest req,
                                   @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.agendarVisita(id, req, usuario.getUsuario());
    }

    @PutMapping("/visitas/{visitaId}/agenda")
    @PreAuthorize(AGENDA)
    public ProyectoDetalle reagendar(@PathVariable UUID visitaId, @Valid @RequestBody ReagendarRequest req,
                                     @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.reagendarVisita(visitaId, req, usuario.getUsuario());
    }

    @PostMapping("/visitas/{visitaId}/cancelar")
    @PreAuthorize(AGENDA)
    public ProyectoDetalle cancelarVisita(@PathVariable UUID visitaId, @Valid @RequestBody VersionRequest req,
                                          @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.cancelarVisita(visitaId, req.version(), usuario.getUsuario());
    }

    /** Personas a las que se les puede asignar una visita. */
    @GetMapping("/personal")
    @PreAuthorize(AGENDA)
    public List<Persona> personal() {
        return service.personal();
    }

    // ---------- cambios de instrucción (RF-PRY-03..08) ----------

    /** Lo propone el equipo de campo (instalador o jefes). */
    @PostMapping("/{id}/cambios")
    @PreAuthorize(CAMPO)
    public ProyectoDetalle proponerCambio(@PathVariable UUID id, @Valid @RequestBody ProponerCambioRequest req,
                                          @AuthenticationPrincipal CustomUserDetails usuario) {
        return cambios.proponer(id, req, usuario.getUsuario());
    }

    @PostMapping(value = "/cambios/{cambioId}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAMPO)
    public ProyectoDetalle fotoCambio(@PathVariable UUID cambioId, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal CustomUserDetails usuario) {
        return cambios.subirFoto(cambioId, file, usuario.getUsuario());
    }

    /** Solo los jefes confirman o rechazan (RF-PRY-04). */
    @PostMapping("/cambios/{cambioId}/confirmar")
    @PreAuthorize(JEFES)
    public ProyectoDetalle confirmarCambio(@PathVariable UUID cambioId, @Valid @RequestBody VersionRequest req,
                                           @AuthenticationPrincipal CustomUserDetails usuario) {
        return cambios.confirmar(cambioId, req.version(), usuario.getUsuario());
    }

    @PostMapping("/cambios/{cambioId}/rechazar")
    @PreAuthorize(JEFES)
    public ProyectoDetalle rechazarCambio(@PathVariable UUID cambioId, @Valid @RequestBody RechazoCambioRequest req,
                                          @AuthenticationPrincipal CustomUserDetails usuario) {
        return cambios.rechazar(cambioId, req, usuario.getUsuario());
    }

    /** Una versión de las instrucciones, para el historial (RF-PRY-07). */
    @GetMapping("/{id}/instrucciones/{numero}")
    @PreAuthorize(LECTORES)
    public VersionInstrucciones version(@PathVariable UUID id, @PathVariable int numero) {
        return service.versionPorNumero(id, numero);
    }

    // ---------- cierre y reporte ----------

    /** Cierra con la firma del cliente (imagen PNG) y el nombre de quien recibe. */
    @PostMapping(value = "/{id}/cierre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(CAMPO)
    public ProyectoDetalle cerrar(@PathVariable UUID id, @RequestParam("firma") MultipartFile firma,
                                  @RequestParam("nombre") String nombre,
                                  @RequestParam(value = "ocurridoEn", required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ocurridoEn,
                                  @AuthenticationPrincipal CustomUserDetails usuario) {
        return cierre.cerrar(id, firma, nombre, ocurridoEn, usuario.getUsuario());
    }

    /** Reporte de entrega en PDF; si el proyecto no está cerrado, una vista previa con marca de agua. */
    @GetMapping("/{id}/reporte")
    @PreAuthorize(LECTORES)
    public ResponseEntity<byte[]> reporte(@PathVariable UUID id) {
        CierreProyectoService.Reporte r = cierre.reporte(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(r.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(r.pdf());
    }
}
