package com.appdomotica.multimedia.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * Archivos en un bucket privado de Cloudflare R2 (API compatible con S3). El bucket nunca se expone:
 * la app descarga siempre por el backend, que revisa permisos. Se activa al definir R2_BUCKET.
 */
@Slf4j
@Service
@ConditionalOnExpression("!'${hauset.storage.r2.bucket:}'.isBlank()")
public class R2StorageService implements StorageService {

    private final S3Client s3;
    private final String bucket;

    public R2StorageService(@Value("${hauset.storage.r2.bucket}") String bucket,
                            @Value("${hauset.storage.r2.account-id:}") String accountId,
                            @Value("${hauset.storage.r2.endpoint:}") String endpoint,
                            @Value("${hauset.storage.r2.access-key-id:}") String accessKeyId,
                            @Value("${hauset.storage.r2.secret-access-key:}") String secretAccessKey) {
        String url = !endpoint.isBlank() ? endpoint.trim() : "https://" + accountId.trim() + ".r2.cloudflarestorage.com";
        if (endpoint.isBlank() && accountId.isBlank()) {
            throw new IllegalStateException("R2_BUCKET requiere R2_ACCOUNT_ID (o R2_ENDPOINT)");
        }
        if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
            throw new IllegalStateException("R2_BUCKET requiere R2_ACCESS_KEY_ID y R2_SECRET_ACCESS_KEY");
        }
        this.bucket = bucket.trim();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(url))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
        log.info("R2StorageService con el bucket {}", this.bucket);
    }

    @PreDestroy
    void cerrar() {
        s3.close();
    }

    @Override
    public String proveedor() {
        return Almacenes.R2;
    }

    @Override
    public String store(String subpath, String filename, byte[] content) {
        String storageKey = subpath.isBlank() ? filename : subpath + "/" + filename;
        if (storageKey.startsWith("/") || storageKey.contains("..")) {
            throw new SecurityException("Ruta no permitida: " + storageKey);
        }
        // Content-MD5: R2 rechaza la subida si los bytes llegan alterados.
        s3.putObject(b -> b.bucket(bucket).key(storageKey).contentLength((long) content.length)
                        .contentMD5(md5Base64(content)),
                RequestBody.fromBytes(content));
        log.debug("Archivo almacenado en R2: {}", storageKey);
        return storageKey;
    }

    @Override
    public InputStream load(String storageKey) {
        return s3.getObject(b -> b.bucket(bucket).key(storageKey));
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        return new ObjetoR2(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(storageKey));
        } catch (S3Exception e) {
            log.warn("No se pudo eliminar el archivo de R2 {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return cabecera(storageKey).isPresent();
    }

    @Override
    public long size(String storageKey) {
        return cabecera(storageKey).map(HeadObjectResponse::contentLength).orElse(0L);
    }

    private Optional<HeadObjectResponse> cabecera(String storageKey) {
        try {
            return Optional.of(s3.headObject(b -> b.bucket(bucket).key(storageKey)));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static String md5Base64(byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 no disponible", e);
        }
    }

    /** Recurso que se descarga de R2 cada vez que se abre (las descargas pasan por el backend). */
    private final class ObjetoR2 extends AbstractResource {
        private final String storageKey;

        ObjetoR2(String storageKey) {
            this.storageKey = storageKey;
        }

        @Override
        public InputStream getInputStream() {
            return load(storageKey);
        }

        @Override
        public boolean exists() {
            return R2StorageService.this.exists(storageKey);
        }

        @Override
        public long contentLength() {
            return size(storageKey);
        }

        @Override
        public String getFilename() {
            return storageKey.substring(storageKey.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "R2 [" + bucket + "/" + storageKey + "]";
        }
    }
}
