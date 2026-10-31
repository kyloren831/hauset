package com.appdomotica.mantenimiento.repository;

import com.appdomotica.mantenimiento.entity.CredencialAccesoLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CredencialAccesoLogRepository extends JpaRepository<CredencialAccesoLog, UUID> {

    @Query("select a from CredencialAccesoLog a join fetch a.consultadoPor where a.credencial.id = :credencial "
            + "order by a.fecha desc")
    List<CredencialAccesoLog> deCredencial(@Param("credencial") UUID credencialId);

    /** Cuántas veces se consultó cada credencial: [id, cantidad]. */
    @Query("select a.credencial.id, count(a) from CredencialAccesoLog a where a.credencial.id in :ids "
            + "group by a.credencial.id")
    List<Object[]> conteos(@Param("ids") Collection<UUID> credencialIds);
}
