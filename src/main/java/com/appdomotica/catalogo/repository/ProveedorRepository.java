package com.appdomotica.catalogo.repository;

import com.appdomotica.catalogo.entity.EstadoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
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
public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {
    List<Proveedor> findByEstadoOrderByNombreAsc(EstadoCatalogo estado);

    /** `patron` ya en minúscula y con comodines (%texto%); vacío = sin filtro de nombre. */
    @Query("select p from Proveedor p where p.estado in :estados "
            + "and (:patron = '' or lower(p.nombre) like :patron or lower(p.contacto) like :patron)")
    Page<Proveedor> buscar(@Param("estados") Collection<EstadoCatalogo> estados, @Param("patron") String patron,
                           Pageable pageable);

    /** Nombre ya usado por otro proveedor no archivado. */
    @Query("select count(p) > 0 from Proveedor p where lower(p.nombre) = lower(:nombre) "
            + "and p.estado <> com.appdomotica.catalogo.entity.EstadoCatalogo.ARCHIVADO "
            + "and (:excluir is null or p.id <> :excluir)")
    boolean nombreEnUso(@Param("nombre") String nombre, @Param("excluir") UUID excluir);
}
