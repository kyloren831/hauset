package com.appdomotica.multimedia.repository;

import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchivoAdjuntoRepository extends JpaRepository<ArchivoAdjunto, UUID> {

    List<ArchivoAdjunto> findByEntidadTipoAndEntidadIdAndDeletedAtIsNull(String entidadTipo, UUID entidadId);

    List<ArchivoAdjunto> findByEntidadTipoAndEntidadIdAndCategoriaAndDeletedAtIsNull(
            String entidadTipo, UUID entidadId, String categoria
    );

    List<ArchivoAdjunto> findByEntidadTipoAndEntidadIdInAndCategoriaAndDeletedAtIsNull(
            String entidadTipo, java.util.Collection<UUID> entidadIds, String categoria
    );

    Optional<ArchivoAdjunto> findByStorageKey(String storageKey);

    Optional<ArchivoAdjunto> findFirstByChecksumSha256AndDeletedAtIsNull(String checksumSha256);
}
