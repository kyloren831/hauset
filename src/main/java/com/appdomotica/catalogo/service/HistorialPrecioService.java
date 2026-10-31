package com.appdomotica.catalogo.service;

import com.appdomotica.catalogo.dto.HistorialPrecioResponse;
import com.appdomotica.catalogo.dto.RegistrarPrecioRequest;
import com.appdomotica.catalogo.entity.HistorialPrecio;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.HistorialPrecioRepository;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.common.dto.PageResponse;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.config.CustomUserDetails;
import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HistorialPrecioService {

    @Autowired
    private HistorialPrecioRepository historialPrecioRepository;

    @Autowired
    private ProductoCatalogoRepository productoCatalogoRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Transactional
    public HistorialPrecioResponse registrarPrecio(RegistrarPrecioRequest request, CustomUserDetails userDetails) {
        ProductoCatalogo producto = productoCatalogoRepository.findById(request.getProductoId())
                .filter(ProductoCatalogo::esActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado o inactivo con ID: " + request.getProductoId()));

        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .filter(Proveedor::esActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado o inactivo con ID: " + request.getProveedorId()));

        Usuario usuario = userDetails != null ? userDetails.getUsuario() : null;

        HistorialPrecio historial = HistorialPrecio.builder()
                .producto(producto)
                .proveedor(proveedor)
                .precioNeto(request.getPrecioNeto())
                .disponible(request.getDisponible() != null ? request.getDisponible() : true)
                .registradoPor(usuario)
                .fecha(LocalDateTime.now())
                .build();

        HistorialPrecio guardado = historialPrecioRepository.save(historial);
        return HistorialPrecioResponse.fromEntity(guardado);
    }

    @Transactional(readOnly = true)
    public PageResponse<HistorialPrecioResponse> listarHistorialPorProducto(UUID productoId, Pageable pageable) {
        if (!productoCatalogoRepository.existsById(productoId)) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + productoId);
        }

        Page<HistorialPrecio> page = historialPrecioRepository.findByProductoIdOrderByFechaDesc(productoId, pageable);
        return PageResponse.from(page.map(HistorialPrecioResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioResponse> listarUltimosPreciosPorProducto(UUID productoId) {
        if (!productoCatalogoRepository.existsById(productoId)) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + productoId);
        }

        return historialPrecioRepository.findUltimosPreciosPorProveedorParaProducto(productoId).stream()
                .map(HistorialPrecioResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<HistorialPrecioResponse> listarHistorialPorProveedor(UUID proveedorId, Pageable pageable) {
        if (!proveedorRepository.existsById(proveedorId)) {
            throw new ResourceNotFoundException("Proveedor no encontrado con ID: " + proveedorId);
        }

        Page<HistorialPrecio> page = historialPrecioRepository.findByProveedorIdOrderByFechaDesc(proveedorId, pageable);
        return PageResponse.from(page.map(HistorialPrecioResponse::fromEntity));
    }
}
