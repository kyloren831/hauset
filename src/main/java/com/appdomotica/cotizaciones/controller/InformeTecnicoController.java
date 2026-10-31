package com.appdomotica.cotizaciones.controller;

import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.InformeRequest;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.InformeResponse;
import com.appdomotica.cotizaciones.dto.CotizacionDtos.VersionInformeRequest;
import com.appdomotica.cotizaciones.service.InformeTecnicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/informes")
@RequiredArgsConstructor
public class InformeTecnicoController {
    private final InformeTecnicoService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA')")
    public PageResponse<InformeResponse> listar(
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFE_SISTEMAS', 'JEFE_ELECTROMECANICO', 'SECRETARIA')")
    public InformeResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    /** Solo el ingeniero en sistemas levanta el informe técnico (RF-COT-02). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('JEFE_SISTEMAS')")
    public InformeResponse crear(@Valid @RequestBody InformeRequest req,
                                 @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.crear(req, usuario.getUsuario());
    }

    /** Foto de una ubicación, con las marcas ya dibujadas en el teléfono. Reemplaza la anterior. */
    @PostMapping(value = "/{id}/ubicaciones/{ubicacionId}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('JEFE_SISTEMAS')")
    public InformeResponse subirFoto(@PathVariable UUID id, @PathVariable UUID ubicacionId,
                                     @RequestParam("file") MultipartFile file,
                                     @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.subirFotoUbicacion(id, ubicacionId, file, usuario.getUsuario());
    }

    @DeleteMapping("/{id}/ubicaciones/{ubicacionId}/foto")
    @PreAuthorize("hasRole('JEFE_SISTEMAS')")
    public InformeResponse eliminarFoto(@PathVariable UUID id, @PathVariable UUID ubicacionId) {
        return service.eliminarFotoUbicacion(id, ubicacionId);
    }

    /** Corrige un informe creando la versión siguiente a partir de esta; la de origen no cambia. */
    @PostMapping("/{id}/versiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('JEFE_SISTEMAS')")
    public InformeResponse nuevaVersion(@PathVariable UUID id, @Valid @RequestBody VersionInformeRequest req,
                                        @AuthenticationPrincipal CustomUserDetails usuario) {
        return service.nuevaVersion(id, req, usuario.getUsuario());
    }
}
