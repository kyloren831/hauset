package com.appdomotica.catalogo.repository;

import com.appdomotica.catalogo.entity.HistorialPrecio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistorialPrecioRepository extends JpaRepository<HistorialPrecio, UUID> {

    Page<HistorialPrecio> findByProductoIdOrderByFechaDesc(UUID productoId, Pageable pageable);

    Page<HistorialPrecio> findByProveedorIdOrderByFechaDesc(UUID proveedorId, Pageable pageable);

    Optional<HistorialPrecio> findTopByProductoIdOrderByFechaDesc(UUID productoId);

    @Query("SELECT h FROM HistorialPrecio h " +
           "WHERE h.producto.id = :productoId " +
           "AND h.fecha = (" +
           "   SELECT MAX(h2.fecha) FROM HistorialPrecio h2 " +
           "   WHERE h2.producto.id = :productoId AND h2.proveedor.id = h.proveedor.id" +
           ") ORDER BY h.precioNeto ASC")
    List<HistorialPrecio> findUltimosPreciosPorProveedorParaProducto(@Param("productoId") UUID productoId);
}
