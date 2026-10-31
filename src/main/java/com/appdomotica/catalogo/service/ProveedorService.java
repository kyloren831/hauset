package com.appdomotica.catalogo.service;

import com.appdomotica.catalogo.dto.FiltroEstado;
import com.appdomotica.catalogo.dto.ProveedorRequest;
import com.appdomotica.catalogo.dto.ProveedorResponse;
import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** Proveedores (RF-CAT-02): activos, inactivos (visibles, no se ofrecen al cotizar) y archivados. */
@Service
@RequiredArgsConstructor
public class ProveedorService {
    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProveedorResponse> listarProveedores(String search, FiltroEstado estado, Pageable pageable) {
        var filtro = estado == null ? FiltroEstado.ACTIVO : estado;
        return PageResponse.from(proveedorRepository.buscar(filtro.estados(), FiltroEstado.patron(search), pageable)
                .map(ProveedorResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listarTodosActivos() {
        return proveedorRepository.findByEstadoOrderByNombreAsc(EstadoCatalogo.ACTIVO).stream()
                .map(ProveedorResponse::fromEntity)
                .toList();
    }

    /** Se puede consultar en cualquier estado. */
    @Transactional(readOnly = true)
    public ProveedorResponse obtenerPorId(UUID id) {
        return ProveedorResponse.fromEntity(buscar(id));
    }

    @Transactional
    public ProveedorResponse crearProveedor(ProveedorRequest request) {
        String nombre = request.getNombre().trim();
        if (proveedorRepository.nombreEnUso(nombre, null)) {
            throw new ConflictException("Ya existe un proveedor con el nombre " + nombre
                    + ". Si está inactivo, actívalo en lugar de crear otro.");
        }
        Proveedor proveedor = new Proveedor();
        aplicar(proveedor, request);
        proveedor.setEstado(EstadoCatalogo.ACTIVO);
        return ProveedorResponse.fromEntity(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizarProveedor(UUID id, ProveedorRequest request) {
        Proveedor proveedor = buscar(id);
        if (proveedor.getEstado() == EstadoCatalogo.ARCHIVADO) {
            throw new ConflictException("El proveedor está archivado; restáuralo antes de editarlo");
        }
        if (proveedorRepository.nombreEnUso(request.getNombre().trim(), id)) {
            throw new ConflictException("Ya existe otro proveedor con el nombre " + request.getNombre().trim());
        }
        aplicar(proveedor, request);
        return ProveedorResponse.fromEntity(proveedorRepository.save(proveedor));
    }

    /** Activar o desactivar. Un archivado primero se restaura. */
    @Transactional
    public ProveedorResponse cambiarEstado(UUID id, EstadoCatalogo nuevo) {
        if (nuevo == EstadoCatalogo.ARCHIVADO) {
            throw new BadRequestException("Para archivar usa la opción Archivar");
        }
        Proveedor proveedor = buscar(id);
        if (proveedor.getEstado() == EstadoCatalogo.ARCHIVADO) {
            throw new ConflictException("El proveedor está archivado; restáuralo primero");
        }
        proveedor.setEstado(nuevo);
        return ProveedorResponse.fromEntity(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse archivar(UUID id) {
        Proveedor proveedor = buscar(id);
        proveedor.setEstado(EstadoCatalogo.ARCHIVADO);
        return ProveedorResponse.fromEntity(proveedorRepository.save(proveedor));
    }

    /** Vuelve como activo, si su nombre no lo tomó otro proveedor mientras estaba archivado. */
    @Transactional
    public ProveedorResponse restaurar(UUID id) {
        Proveedor proveedor = buscar(id);
        if (proveedor.getEstado() != EstadoCatalogo.ARCHIVADO) {
            return ProveedorResponse.fromEntity(proveedor);
        }
        if (proveedorRepository.nombreEnUso(proveedor.getNombre(), id)) {
            throw new ConflictException("Ya existe otro proveedor con el nombre " + proveedor.getNombre()
                    + "; cambia uno de los dos antes de restaurar");
        }
        proveedor.setEstado(EstadoCatalogo.ACTIVO);
        return ProveedorResponse.fromEntity(proveedorRepository.save(proveedor));
    }

    private void aplicar(Proveedor proveedor, ProveedorRequest request) {
        proveedor.setNombre(request.getNombre().trim());
        proveedor.setCategoriasAsociadas(normalizarCategorias(request.getCategoriasAsociadas()));
        proveedor.setContacto(request.getContacto().trim());
    }

    /** "Seguridad,CONTROL_ACCESO " → "seguridad, control_acceso". Rechaza categorías que no existen. */
    static String normalizarCategorias(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return Arrays.stream(texto.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT).replace(' ', '_'))
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(s -> {
                    try {
                        return CategoriaProducto.valueOf(s).name().toLowerCase(Locale.ROOT);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Categoría desconocida: " + s);
                    }
                })
                .collect(Collectors.joining(", "));
    }

    private Proveedor buscar(UUID id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));
    }
}
