package com.appdomotica.multimedia;

import com.appdomotica.multimedia.dto.ArchivoAdjuntoResponse;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ArchivoAdjuntoIntegrationTest {

    @Autowired
    private ArchivoAdjuntoService archivoService;

    @Autowired
    private StorageService storageService;

    @Test
    void testSubirYRecuperarArchivoEnDisco() {
        UUID tareaId = UUID.randomUUID();
        byte[] contenido = "Contenido de prueba de evidencia fotografica en obra".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "evidencia_antes.jpg", "image/jpeg", contenido
        );

        // 1. Subir archivo
        ArchivoAdjuntoResponse response = archivoService.subirArchivo(
                file, "CHECKLIST_TAREA", tareaId, "EVIDENCIA_ANTES", "Foto de tablero inicial", null
        );

        assertNotNull(response.getId());
        assertEquals("CHECKLIST_TAREA", response.getEntidadTipo());
        assertEquals(tareaId, response.getEntidadId());
        assertEquals("EVIDENCIA_ANTES", response.getCategoria());
        assertNotNull(response.getStorageKey());
        assertTrue(storageService.exists(response.getStorageKey()));

        // 2. Cargar y verificar contenido desde disco
        ArchivoAdjunto entidad = archivoService.obtenerEntidad(response.getId());
        Resource recurso = archivoService.cargarContenido(entidad);
        assertTrue(recurso.exists());
        assertTrue(recurso.isReadable());

        // 3. Listar por entidad
        List<ArchivoAdjuntoResponse> lista = archivoService.listarPorEntidad("CHECKLIST_TAREA", tareaId);
        assertEquals(1, lista.size());
        assertEquals(response.getId(), lista.get(0).getId());

        // 4. Probar idempotencia: subir el mismo archivo para la misma tarea devuelve la entidad ya existente
        ArchivoAdjuntoResponse duplicado = archivoService.subirArchivo(
                file, "CHECKLIST_TAREA", tareaId, "EVIDENCIA_ANTES", "Misma foto subida de nuevo", null
        );
        assertEquals(response.getId(), duplicado.getId());
        assertEquals(response.getStorageKey(), duplicado.getStorageKey());

        // 5. Soft delete
        archivoService.eliminarArchivo(response.getId());
        List<ArchivoAdjuntoResponse> listaPostDelete = archivoService.listarPorEntidad("CHECKLIST_TAREA", tareaId);
        assertEquals(0, listaPostDelete.size());
    }
}
