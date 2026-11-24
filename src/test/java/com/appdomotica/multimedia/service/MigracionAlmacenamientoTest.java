package com.appdomotica.multimedia.service;

import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MigracionAlmacenamientoTest {

    /** Almacén en memoria con el proveedor que se le indique. */
    static class EnMemoria implements StorageService {
        final String proveedor;
        final Map<String, byte[]> datos = new HashMap<>();

        EnMemoria(String proveedor) {
            this.proveedor = proveedor;
        }

        @Override public String proveedor() { return proveedor; }
        @Override public String store(String subpath, String filename, byte[] content) {
            String clave = subpath.isEmpty() ? filename : subpath + "/" + filename;
            datos.put(clave, content);
            return clave;
        }
        @Override public InputStream load(String k) { return new ByteArrayInputStream(datos.get(k)); }
        @Override public Resource loadAsResource(String k) { return new ByteArrayResource(datos.get(k)); }
        @Override public void delete(String k) { datos.remove(k); }
        @Override public boolean exists(String k) { return datos.containsKey(k); }
        @Override public long size(String k) { return datos.containsKey(k) ? datos.get(k).length : 0; }
    }

    private final EnMemoria disco = new EnMemoria(Almacenes.DISCO_LOCAL);
    private final EnMemoria r2 = new EnMemoria(Almacenes.R2);
    private final ArchivoAdjuntoRepository repo = mock(ArchivoAdjuntoRepository.class);

    private ArchivoAdjunto enDisco(String clave, byte[] bytes) throws Exception {
        disco.datos.put(clave, bytes);
        return ArchivoAdjunto.builder().storageProvider(Almacenes.DISCO_LOCAL).storageKey(clave)
                .checksumSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                .build();
    }

    @Test
    void copiaAR2ConLaMismaClaveYCambiaElRegistroSinBorrarElDisco() throws Exception {
        ArchivoAdjunto foto = enDisco("checklist_tarea/2026/11/a.jpg", new byte[]{1, 2, 3});
        when(repo.findByStorageProviderNotAndDeletedAtIsNull(Almacenes.R2)).thenReturn(List.of(foto));

        int migrados = new MigracionAlmacenamiento(repo, new Almacenes(List.of(disco, r2), "R2")).migrar();

        assertEquals(1, migrados);
        assertArrayEquals(new byte[]{1, 2, 3}, r2.datos.get("checklist_tarea/2026/11/a.jpg"));
        assertTrue(disco.exists("checklist_tarea/2026/11/a.jpg"));
        assertEquals(Almacenes.R2, foto.getStorageProvider());
        verify(repo).save(foto);
    }

    @Test
    void unArchivoQueFaltaEnDiscoNoDetieneLosDemasYQuedaPendiente() throws Exception {
        ArchivoAdjunto perdido = ArchivoAdjunto.builder().storageProvider(Almacenes.DISCO_LOCAL)
                .storageKey("x/no-existe.jpg").checksumSha256("0").build();
        ArchivoAdjunto bueno = enDisco("x/b.pdf", new byte[]{9});
        when(repo.findByStorageProviderNotAndDeletedAtIsNull(Almacenes.R2)).thenReturn(List.of(perdido, bueno));

        int migrados = new MigracionAlmacenamiento(repo, new Almacenes(List.of(disco, r2), "R2")).migrar();

        assertEquals(1, migrados);
        assertEquals(Almacenes.DISCO_LOCAL, perdido.getStorageProvider());
        assertEquals(Almacenes.R2, bueno.getStorageProvider());
        verify(repo, never()).save(perdido);
    }

    @Test
    void conElDiscoActivoNoHayNadaQueMigrar() {
        when(repo.findByStorageProviderNotAndDeletedAtIsNull(Almacenes.DISCO_LOCAL)).thenReturn(List.of());

        assertEquals(0, new MigracionAlmacenamiento(repo, new Almacenes(List.of(disco), "DISCO_LOCAL")).migrar());
        verify(repo, never()).save(any());
    }

    @Test
    void elegirR2SinConfigurarloImpideArrancar() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new Almacenes(List.of(disco), "R2"));
        assertTrue(e.getMessage().contains("R2_BUCKET"));
    }
}
