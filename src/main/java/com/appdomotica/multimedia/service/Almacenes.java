package com.appdomotica.multimedia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los archivos nuevos van al almacén activo (STORAGE_PROVEEDOR); cada archivo se lee del almacén que
 * dice su registro, así conviven los que siguen en disco con los que ya están en R2 mientras se migra.
 */
@Slf4j
@Component
public class Almacenes {

    public static final String DISCO_LOCAL = "DISCO_LOCAL";
    public static final String R2 = "R2";

    private final Map<String, StorageService> porProveedor;
    private final StorageService activo;

    public Almacenes(List<StorageService> almacenes,
                     @Value("${hauset.storage.proveedor:DISCO_LOCAL}") String proveedorActivo) {
        this.porProveedor = almacenes.stream()
                .collect(Collectors.toMap(StorageService::proveedor, Function.identity()));
        this.activo = porProveedor.get(proveedorActivo.trim().toUpperCase());
        if (activo == null) {
            throw new IllegalStateException("STORAGE_PROVEEDOR=" + proveedorActivo + " no está configurado; disponibles: "
                    + porProveedor.keySet() + " (R2 requiere R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY y R2_ACCOUNT_ID o R2_ENDPOINT)");
        }
        log.info("Archivos nuevos en {}", activo.proveedor());
    }

    public StorageService activo() {
        return activo;
    }

    public StorageService de(String proveedor) {
        StorageService almacen = porProveedor.get(proveedor);
        if (almacen == null) {
            throw new IllegalStateException("El archivo está en " + proveedor + " y ese almacén no está configurado");
        }
        return almacen;
    }
}
