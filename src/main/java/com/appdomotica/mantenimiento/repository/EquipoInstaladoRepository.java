package com.appdomotica.mantenimiento.repository;

import com.appdomotica.mantenimiento.entity.EquipoInstalado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EquipoInstaladoRepository extends JpaRepository<EquipoInstalado, UUID> {

    @Query("select e from EquipoInstalado e left join fetch e.producto where e.proyecto.id = :proyecto and e.activo "
            + "order by e.createdAt")
    List<EquipoInstalado> activosDe(@Param("proyecto") UUID proyectoId);

    @Query("select e from EquipoInstalado e join fetch e.proyecto p join fetch p.cotizacion c join fetch c.cliente "
            + "left join fetch e.producto where e.activo order by e.createdAt")
    List<EquipoInstalado> activos();

    boolean existsByDireccionMacAndActivoTrueAndIdNot(String mac, UUID excepto);

    @Query("select count(e) > 0 from EquipoInstalado e where e.activo and e.producto.id = :producto "
            + "and upper(e.numeroSerie) = upper(:serie) and e.id <> :excepto")
    boolean serieOcupada(@Param("producto") UUID productoId, @Param("serie") String serie,
                         @Param("excepto") UUID excepto);

    /** Al cerrar el proyecto (RF-MAN-02). */
    @Modifying
    @Query("update EquipoInstalado e set e.fechaInstalacion = :fecha where e.proyecto.id = :proyecto "
            + "and e.fechaInstalacion is null")
    int fijarFechaInstalacion(@Param("proyecto") UUID proyectoId, @Param("fecha") LocalDate fecha);
}
