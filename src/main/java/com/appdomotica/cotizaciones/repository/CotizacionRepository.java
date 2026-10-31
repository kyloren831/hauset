package com.appdomotica.cotizaciones.repository;

import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.entity.EstadoCotizacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {
    Page<Cotizacion> findByEstado(EstadoCotizacion estado, Pageable pageable);


    boolean existsByInformeId(UUID informeId);

    java.util.Optional<Cotizacion> findByInformeId(UUID informeId);

    java.util.List<Cotizacion> findByInformeIdIn(java.util.Collection<UUID> informeIds);

    /** Aceptadas que todavía no se iniciaron como proyecto (RF-COT-17), las más antiguas primero. */
    @Query("select c from Cotizacion c where c.estado = com.appdomotica.cotizaciones.entity.EstadoCotizacion.ACEPTADA "
            + "and not exists (select 1 from Proyecto p where p.cotizacion = c) order by c.fechaRespuesta asc")
    java.util.List<Cotizacion> aceptadasSinProyecto();

    @Query(value = "select nextval('cotizacion_numero_seq')", nativeQuery = true)
    long siguienteNumero();
}
