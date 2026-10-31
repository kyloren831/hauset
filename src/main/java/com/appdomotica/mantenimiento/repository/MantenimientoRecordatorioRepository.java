package com.appdomotica.mantenimiento.repository;

import com.appdomotica.mantenimiento.entity.EstadoRecordatorio;
import com.appdomotica.mantenimiento.entity.MantenimientoRecordatorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MantenimientoRecordatorioRepository extends JpaRepository<MantenimientoRecordatorio, UUID> {

    @Query("select r from MantenimientoRecordatorio r join fetch r.proyecto p join fetch p.cotizacion c "
            + "join fetch c.cliente left join fetch r.contactadoPor where r.estado = :estado order by r.fechaProgramada")
    List<MantenimientoRecordatorio> conEstado(@Param("estado") EstadoRecordatorio estado);

    @Query("select r from MantenimientoRecordatorio r join fetch r.proyecto p join fetch p.cotizacion c "
            + "join fetch c.cliente left join fetch r.contactadoPor where r.estado <> 'PENDIENTE' "
            + "and r.fechaContacto >= :desde order by r.fechaContacto desc")
    List<MantenimientoRecordatorio> resueltosDesde(@Param("desde") OffsetDateTime desde);

    List<MantenimientoRecordatorio> findByProyectoId(UUID proyectoId);
}
