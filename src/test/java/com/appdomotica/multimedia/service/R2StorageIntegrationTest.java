package com.appdomotica.multimedia.service;

import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Contra un servicio compatible con S3 (MinIO en el sandbox), nunca contra el R2 real. Solo corre con
 * HAUSET_PRUEBA_S3_ENDPOINT, _BUCKET, _USUARIO y _CLAVE definidas; el bucket debe existir.
 */
@SpringBootTest(properties = {
        "hauset.storage.proveedor=R2",
        "hauset.storage.r2.endpoint=${HAUSET_PRUEBA_S3_ENDPOINT}",
        "hauset.storage.r2.bucket=${HAUSET_PRUEBA_S3_BUCKET}",
        "hauset.storage.r2.access-key-id=${HAUSET_PRUEBA_S3_USUARIO}",
        "hauset.storage.r2.secret-access-key=${HAUSET_PRUEBA_S3_CLAVE}"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "HAUSET_PRUEBA_S3_ENDPOINT", matches = ".+")
class R2StorageIntegrationTest {

    @Autowired private ArchivoAdjuntoService archivos;
    @Autowired private ArchivoAdjuntoRepository repo;
    @Autowired private Almacenes almacenes;
    @Autowired private MigracionAlmacenamiento migracion;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    @Test
    void laAppDescargaElArchivoDelBucketPorElBackend() throws Exception {
        byte[] foto = ("descarga " + UUID.randomUUID()).getBytes();
        var subido = archivos.subirArchivo(new MockMultipartFile("file", "d.jpg", "image/jpeg", foto),
                "CHECKLIST_TAREA", UUID.randomUUID(), "EVIDENCIA_DESPUES", null, null);

        Usuario jefe = new Usuario();
        jefe.setNombre("Jefe R2");
        jefe.setRol(RolUsuario.JEFE_SISTEMAS);
        jefe.setTelefono("6" + String.format("%07d", (int) (System.nanoTime() % 10_000_000)));
        jefe.setPasswordHash(encoder.encode("clave-de-prueba-123"));
        jefe.setActivo(true);
        usuarios.save(jefe);
        MvcResult login = mvc.perform(post("/api/usuarios/login").with(r -> {
            r.setRemoteAddr("192.0.2.40");
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("telefono", jefe.getTelefono(), "password", "clave-de-prueba-123")))).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("token").asText();

        MvcResult descarga = mvc.perform(get(subido.getUrlDescarga()).header("Authorization", "Bearer " + token))
                .andReturn();
        assertEquals(200, descarga.getResponse().getStatus());
        assertEquals("image/jpeg", descarga.getResponse().getContentType());
        assertArrayEquals(foto, descarga.getResponse().getContentAsByteArray());
    }

    @Test
    void losArchivosNuevosVanAlBucketYSeLeenDeAhi() throws Exception {
        byte[] foto = ("foto " + UUID.randomUUID()).getBytes();
        var subido = archivos.subirArchivo(new MockMultipartFile("file", "f.jpg", "image/jpeg", foto),
                "CHECKLIST_TAREA", UUID.randomUUID(), "EVIDENCIA_ANTES", null, null);

        ArchivoAdjunto registro = archivos.obtenerEntidad(subido.getId());
        assertEquals(Almacenes.R2, registro.getStorageProvider());
        assertFalse(almacenes.de(Almacenes.DISCO_LOCAL).exists(registro.getStorageKey()));
        var recurso = archivos.cargarContenido(registro);
        assertEquals(foto.length, recurso.contentLength());
        try (InputStream in = recurso.getInputStream()) {
            assertArrayEquals(foto, in.readAllBytes());
        }
    }

    @Test
    void unArchivoQueSigueEnDiscoSeLeeDelDiscoHastaQueSeMigra() throws Exception {
        byte[] pdf = ("pdf " + UUID.randomUUID()).getBytes();
        StorageService disco = almacenes.de(Almacenes.DISCO_LOCAL);
        // Simula un archivo de antes de R2 con el flujo de siempre en disco.
        String clave = disco.store("cotizacion/2026/11", UUID.randomUUID() + ".pdf", pdf);
        ArchivoAdjunto viejo = repo.save(ArchivoAdjunto.builder().entidadTipo("COTIZACION")
                .entidadId(UUID.randomUUID()).categoria("DOCUMENTO_PDF").storageProvider(Almacenes.DISCO_LOCAL)
                .storageKey(clave).nombreOriginal("c.pdf").mimeType("application/pdf")
                .tamanoBytes((long) pdf.length).checksumSha256("x").build());

        try (InputStream in = archivos.cargarContenido(viejo).getInputStream()) {
            assertArrayEquals(pdf, in.readAllBytes());
        }

        assertTrue(migracion.migrar() >= 1);

        ArchivoAdjunto migrado = archivos.obtenerEntidad(viejo.getId());
        assertEquals(Almacenes.R2, migrado.getStorageProvider());
        assertTrue(disco.exists(clave), "el original queda en disco");
        try (InputStream in = archivos.cargarContenido(migrado).getInputStream()) {
            assertArrayEquals(pdf, in.readAllBytes());
        }
    }
}
