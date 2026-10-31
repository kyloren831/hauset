package com.appdomotica.catalogo.repository;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductoCatalogoRepository extends JpaRepository<ProductoCatalogo, UUID> {
    List<ProductoCatalogo> findByEstadoOrderByNombreAsc(EstadoCatalogo estado);

    /** `categorias` vacío no se usa: se pasa la lista completa para "todas". `patron` vacío = sin filtro. */
    @Query("select p from ProductoCatalogo p where p.estado in :estados and p.categoria in :categorias "
            + "and (:patron = '' or lower(p.nombre) like :patron or lower(p.especificacion) like :patron)")
    Page<ProductoCatalogo> buscar(@Param("estados") Collection<EstadoCatalogo> estados,
                                  @Param("categorias") Collection<CategoriaProducto> categorias,
                                  @Param("patron") String patron, Pageable pageable);

    /** Nombre ya usado por otro producto no archivado. */
    @Query("select count(p) > 0 from ProductoCatalogo p where lower(p.nombre) = lower(:nombre) "
            + "and p.estado <> com.appdomotica.catalogo.entity.EstadoCatalogo.ARCHIVADO "
            + "and (:excluir is null or p.id <> :excluir)")
    boolean nombreEnUso(@Param("nombre") String nombre, @Param("excluir") UUID excluir);
}
