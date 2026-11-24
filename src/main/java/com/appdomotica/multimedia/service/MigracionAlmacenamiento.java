package com.appdomotica.multimedia.service;

import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Al arrancar con un almacén activo distinto del disco (R2), copia ahí los archivos vigentes que siguen
 * en otro almacén y cambia su registro (los borrados ya no se descargan y se quedan donde estaban). Es reanudable: un archivo pasa solo cuando ya se copió y se comprobó
 * su tamaño. Los originales no se borran; quitarlos del disco es un paso manual tras revisar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigracionAlmacenamiento implements ApplicationRunner {

    private final ArchivoAdjuntoRepository archivos;
    private final Almacenes almacenes;

    @Override
    public void run(ApplicationArguments args) {
        migrar();
    }

    /** Devuelve cuántos archivos pasaron al almacén activo en esta ejecución. */
    public int migrar() {
        StorageService destino = almacenes.activo();
        var pendientes = archivos.findByStorageProviderNotAndDeletedAtIsNull(destino.proveedor());
        if (pendientes.isEmpty()) {
            return 0;
        }
        log.info("Migrando {} archivos a {}", pendientes.size(), destino.proveedor());
        int migrados = 0;
        int fallidos = 0;
        for (ArchivoAdjunto archivo : pendientes) {
            try {
                if (migrar(archivo, destino)) {
                    migrados++;
                } else {
                    fallidos++;
                }
            } catch (RuntimeException e) {
                fallidos++;
                log.warn("No se pudo migrar el archivo {} ({}): {}", archivo.getId(), archivo.getStorageKey(), e.getMessage());
            }
        }
        log.info("Migración a {}: {} copiados, {} pendientes (se reintentan al próximo arranque)",
                destino.proveedor(), migrados, fallidos);
        return migrados;
    }

    private boolean migrar(ArchivoAdjunto archivo, StorageService destino) {
        StorageService origen = almacenes.de(archivo.getStorageProvider());
        String clave = archivo.getStorageKey();
        if (!origen.exists(clave)) {
            log.warn("El archivo {} no está en {} ({}); se deja como está", archivo.getId(), origen.proveedor(), clave);
            return false;
        }
        byte[] bytes;
        try (InputStream in = origen.load(clave)) {
            bytes = in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error leyendo " + clave, e);
        }
        if (!sha256(bytes).equals(archivo.getChecksumSha256())) {
            log.warn("El archivo {} no coincide con su checksum registrado; se copia igual", archivo.getId());
        }
        int corte = clave.lastIndexOf('/');
        String guardada = destino.store(corte < 0 ? "" : clave.substring(0, corte), clave.substring(corte + 1), bytes);
        if (!guardada.equals(clave) || destino.size(clave) != bytes.length) {
            log.warn("La copia de {} en {} no quedó completa; se reintenta al próximo arranque", clave, destino.proveedor());
            return false;
        }
        archivo.setStorageProvider(destino.proveedor());
        archivos.save(archivo);
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
