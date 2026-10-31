package com.appdomotica.cotizaciones.repository;

import com.appdomotica.cotizaciones.entity.CotizacionExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CotizacionExtraRepository extends JpaRepository<CotizacionExtra, UUID> {
    List<CotizacionExtra> findByCotizacionIdOrderByNumeroAsc(UUID cotizacionId);

    @Query("select coalesce(max(e.numero), 0) + 1 from CotizacionExtra e where e.cotizacion.id = :cotizacion")
    int siguienteNumero(@Param("cotizacion") UUID cotizacion);
}
