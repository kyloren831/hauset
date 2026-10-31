package com.appdomotica.mantenimiento.repository;

import com.appdomotica.mantenimiento.entity.CredencialEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CredencialEquipoRepository extends JpaRepository<CredencialEquipo, UUID> {

    @Query("select c from CredencialEquipo c left join fetch c.actualizadoPor where c.equipo.id in :equipos "
            + "and c.activo order by c.createdAt")
    List<CredencialEquipo> activasDe(@Param("equipos") Collection<UUID> equipoIds);
}
