package com.appdomotica.multimedia.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
@Slf4j
public class DiskStorageService implements StorageService {

    @Value("${hauset.storage.disk.base-path:./storage_data}")
    private String basePathString;

    private Path rootPath;

    @PostConstruct
    public void init() {
        try {
            this.rootPath = Paths.get(basePathString).toAbsolutePath().normalize();
            Files.createDirectories(this.rootPath);
            log.info("DiskStorageService inicializado en el directorio: {}", this.rootPath);
        } catch (IOException e) {
            log.error("No se pudo inicializar el directorio de almacenamiento en disco: {}", e.getMessage());
            throw new RuntimeException("Error inicializando directorio de almacenamiento en disco", e);
        }
    }

    private Path resolveSafePath(String storageKey) {
        Path resolved = this.rootPath.resolve(storageKey).normalize();
        if (!resolved.startsWith(this.rootPath)) {
            throw new SecurityException("Intento de acceso fuera del directorio de almacenamiento: " + storageKey);
        }
        return resolved;
    }

    @Override
    public String store(String subpath, String filename, byte[] content) {
        try {
            Path targetDir = this.rootPath.resolve(subpath).normalize();
            if (!targetDir.startsWith(this.rootPath)) {
                throw new SecurityException("Ruta no permitida: " + subpath);
            }
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(filename).normalize();
            Files.write(targetFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String storageKey = this.rootPath.relativize(targetFile).toString().replace('\\', '/');
            log.debug("Archivo almacenado exitosamente en disco: {}", storageKey);
            return storageKey;
        } catch (IOException e) {
            log.error("Error al escribir archivo en disco: {}", e.getMessage());
            throw new RuntimeException("Error escribiendo archivo en disco", e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            Path targetFile = resolveSafePath(storageKey);
            if (!Files.exists(targetFile) || !Files.isReadable(targetFile)) {
                throw new RuntimeException("El archivo no existe o no se puede leer: " + storageKey);
            }
            return Files.newInputStream(targetFile);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo archivo: " + storageKey, e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        try {
            Path targetFile = resolveSafePath(storageKey);
            Resource resource = new UrlResource(targetFile.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("No se pudo cargar el recurso: " + storageKey);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL del archivo no válida: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path targetFile = resolveSafePath(storageKey);
            Files.deleteIfExists(targetFile);
            log.debug("Archivo eliminado del disco: {}", storageKey);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo del disco {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            Path targetFile = resolveSafePath(storageKey);
            return Files.exists(targetFile);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long size(String storageKey) {
        try {
            Path targetFile = resolveSafePath(storageKey);
            return Files.size(targetFile);
        } catch (IOException e) {
            return 0L;
        }
    }
}
