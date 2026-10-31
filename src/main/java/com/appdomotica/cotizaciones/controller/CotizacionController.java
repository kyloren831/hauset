package com.appdomotica.cotizaciones.controller;

import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.*;
import com.appdomotica.cotizaciones.entity.EstadoCotizacion;
import com.appdomotica.cotizaciones.service.CotizacionService;
import com.appdomotica.cotizaciones.service.ParametroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Cotizaciones: solo jefes y secretaria. El instalador nunca ve precios netos ni márgenes (RN-03). */
@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA')")
public class CotizacionController {
    private final CotizacionService service;
    private final ParametroService parametros;
    private final com.appdomotica.cotizaciones.service.ExtraMaterialesService extras;

    @GetMapping
    public PageResponse<CotizacionResumen> listar(
            @RequestParam(required = false) EstadoCotizacion estado,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(estado, pageable);
    }

    @GetMapping("/{id}")
    public CotizacionResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CotizacionResponse crear(@Valid @RequestBody NuevaCotizacionRequest req,
                                    @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.crear(req, usuario.getUsuario());
    }

    @PutMapping("/{id}/items")
    public CotizacionResponse actualizarItems(@PathVariable UUID id, @Valid @RequestBody ItemsRequest req) {
        return service.actualizarItems(id, req);
    }

    /** El margen lo deciden los jefes (RN-03). */
    @PutMapping("/{id}/margenes")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
    public CotizacionResponse definirMargenes(@PathVariable UUID id, @Valid @RequestBody MargenesRequest req,
                                              @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.definirMargenes(id, req, usuario.getUsuario());
    }

    @PutMapping("/{id}/iva")
    public CotizacionResponse definirIva(@PathVariable UUID id, @Valid @RequestBody IvaRequest req,
                                         @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.definirIva(id, req, usuario.getUsuario());
    }

    @PostMapping("/{id}/enviar")
    public CotizacionResponse enviar(@PathVariable UUID id, @Valid @RequestBody AccionRequest req,
                                     @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.enviar(id, req, usuario.getUsuario());
    }

    @PostMapping("/{id}/aceptar")
    public CotizacionResponse aceptar(@PathVariable UUID id, @Valid @RequestBody AccionRequest req,
                                      @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.aceptar(id, req, usuario.getUsuario());
    }

    /** Convierte una cotización aceptada (con informe técnico) en proyecto (RF-COT-09). */
    @PostMapping("/{id}/iniciar-proyecto")
    public CotizacionResponse iniciarProyecto(@PathVariable UUID id, @Valid @RequestBody AccionRequest req,
                                              @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.iniciarProyecto(id, req, usuario.getUsuario());
    }

    /** Aceptadas sin proyecto todavía: el aviso de la pantalla de cotizaciones (RF-COT-17/18). */
    @GetMapping("/por-iniciar")
    public java.util.List<CotizacionResumen> porIniciar() {
        return service.porIniciar();
    }

    /** Elimina un borrador que nunca se envió al cliente (RF-COT-19). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id, @RequestParam(required = false) Long version) {
        service.eliminar(id, version);
    }

    @PostMapping("/{id}/rechazar")
    public CotizacionResponse rechazar(@PathVariable UUID id, @Valid @RequestBody RechazoRequest req) {
        return service.rechazar(id, req);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var archivo = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(archivo.contenido());
    }

    // ---------- Extras de materiales (RF-COT-10/11) ----------

    @PostMapping("/{id}/extras")
    @ResponseStatus(HttpStatus.CREATED)
    public CotizacionResponse crearExtra(@PathVariable UUID id, @Valid @RequestBody NuevoExtraRequest req,
                                         @AuthenticationPrincipal CustomUserDetails usuario) {
        return extras.crear(id, req, usuario.getUsuario());
    }

    @PutMapping("/extras/{extraId}/items")
    public CotizacionResponse itemsExtra(@PathVariable UUID extraId, @Valid @RequestBody ItemsRequest req) {
        return extras.actualizarItems(extraId, req);
    }

    @PutMapping("/extras/{extraId}/margenes")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
    public CotizacionResponse margenesExtra(@PathVariable UUID extraId, @Valid @RequestBody MargenesRequest req,
                                           @AuthenticationPrincipal CustomUserDetails usuario) {
        return extras.definirMargenes(extraId, req, usuario.getUsuario());
    }

    /** Solo un jefe confirma: desde ahí suma al total facturado. */
    @PostMapping("/extras/{extraId}/confirmar")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
    public CotizacionResponse confirmarExtra(@PathVariable UUID extraId, @Valid @RequestBody AccionRequest req,
                                             @AuthenticationPrincipal CustomUserDetails usuario) {
        return extras.confirmar(extraId, req, usuario.getUsuario());
    }

    @PostMapping("/extras/{extraId}/descartar")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
    public CotizacionResponse descartarExtra(@PathVariable UUID extraId, @Valid @RequestBody DescarteRequest req,
                                             @AuthenticationPrincipal CustomUserDetails usuario) {
        return extras.descartar(extraId, req, usuario.getUsuario());
    }

    @GetMapping("/iva-defecto")
    public IvaDefectoResponse ivaDefecto() {
        return new IvaDefectoResponse(parametros.ivaPorDefecto());
    }

    /** Cambiar el IVA por defecto solo afecta a las cotizaciones que se creen después. */
    @PutMapping("/iva-defecto")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO')")
    public IvaDefectoResponse cambiarIvaDefecto(@Valid @RequestBody IvaDefectoRequest req,
                                                @AuthenticationPrincipal CustomUserDetails usuario) {
        return new IvaDefectoResponse(parametros.cambiarIvaPorDefecto(req.porcentaje(), usuario.getUsuario()));
    }
}
