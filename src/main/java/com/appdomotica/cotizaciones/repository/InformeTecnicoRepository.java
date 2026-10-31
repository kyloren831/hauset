package com.appdomotica.cotizaciones.repository;

import com.appdomotica.cotizaciones.entity.InformeTecnico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import java.util.UUID;

public interface InformeTecnicoRepository extends JpaRepository<InformeTecnico, UUID> {
    Page<InformeTecnico> findByLeadId(UUID leadId, Pageable pageable);

    /** Solo la última versión de cada informe. */
    @Query("select i from InformeTecnico i where i.numeroVersion = "
            + "(select max(j.numeroVersion) from InformeTecnico j where j.grupoId = i.grupoId)")
    Page<InformeTecnico> ultimasVersiones(Pageable pageable);

    List<InformeTecnico> findByGrupoIdInOrderByNumeroVersionAsc(Collection<UUID> grupos);

    @Query("select coalesce(max(i.numeroVersion), 0) from InformeTecnico i where i.grupoId = :grupo")
    int ultimaVersion(@Param("grupo") UUID grupo);
}
