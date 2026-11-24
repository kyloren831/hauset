package com.appdomotica.multimedia.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;

/** Dónde viven los bytes de los archivos adjuntos. Cada registro guarda en storage_provider cuál lo tiene. */
public interface StorageService {

    /** Valor de archivos_adjuntos.storage_provider para este almacén (DISCO_LOCAL, R2). */
    String proveedor();

    String store(String subpath, String filename, byte[] content);

    InputStream load(String storageKey);

    Resource loadAsResource(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);

    long size(String storageKey);
}
