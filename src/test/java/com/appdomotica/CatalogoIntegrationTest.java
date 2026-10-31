package com.appdomotica;

import com.appdomotica.catalogo.dto.ProveedorRequest;
import com.appdomotica.catalogo.dto.RegistrarPrecioRequest;
import com.appdomotica.catalogo.service.HistorialPrecioService;
import com.appdomotica.catalogo.service.ProveedorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CatalogoIntegrationTest {

    @Autowired(required = false)
    private ProveedorService proveedorService;

    @Autowired(required = false)
    private HistorialPrecioService historialPrecioService;

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring cargue correctamente
        assertNotNull(proveedorService);
        assertNotNull(historialPrecioService);
    }
}
