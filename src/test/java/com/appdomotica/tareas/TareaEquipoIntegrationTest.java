package com.appdomotica.tareas;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Lista de tareas de la oficina: tablero, públicas y privadas, quién las ve (SRS 3.8). */
@SpringBootTest
@AutoConfigureMockMvc
class TareaEquipoIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000) + 500_000);
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    private String secretaria, jefe, otroJefe, instalador, marketing;
    private UUID idSecretaria, idInstalador;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        idSecretaria = ultimoId;
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        otroJefe = token(RolUsuario.JEFE_ELECTROMECANICO);
        instalador = token(RolUsuario.INSTALADOR);
        idInstalador = ultimoId;
        marketing = token(RolUsuario.MARKETING);
    }

    private UUID ultimoId;

    private String token(RolUsuario rol) throws Exception {
        String telefono = "6" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
        Usuario u = new Usuario();
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setTelefono(telefono);
        u.setPasswordHash(encoder.encode(CLAVE));
        u.setActivo(true);
        ultimoId = usuarios.save(u).getId();
        MvcResult r = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", telefono, "password", CLAVE)))).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private record Resp(int status, JsonNode body) {
    }

    private Resp llamar(MockHttpServletRequestBuilder req, String bearer, Object cuerpo) throws Exception {
        req.header("Authorization", bearer);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(req).andReturn();
        String texto = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new Resp(r.getResponse().getStatus(), texto.isBlank() ? null : json.readTree(texto));
    }

    private JsonNode crear(String bearer, Map<String, Object> campos) throws Exception {
        Resp r = llamar(post("/api/tareas-equipo"), bearer, campos);
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    private JsonNode buscar(String bearer, String id) throws Exception {
        for (JsonNode t : llamar(get("/api/tareas-equipo"), bearer, null).body()) {
            if (t.get("id").asText().equals(id)) return t;
        }
        return null;
    }

    private Resp mover(String bearer, JsonNode t, String estado) throws Exception {
        return llamar(post("/api/tareas-equipo/" + t.get("id").asText() + "/mover"), bearer,
                Map.of("version", t.get("version").asLong(), "estado", estado));
    }

    @Test
    void unaTareaPublicaRecorreElTableroYQuedaQuienLaTermino() throws Exception {
        JsonNode t = crear(secretaria, Map.of("titulo", "  Llamar al proveedor de cámaras  ",
                "fechaLimite", "2026-11-05", "asignadaAId", idSecretaria.toString()));
        assertEquals("Llamar al proveedor de cámaras", t.get("titulo").asText());
        assertEquals("POR_HACER", t.get("estado").asText());
        assertEquals("MANUAL", t.get("origen").asText());

        // Los dos jefes la ven y cualquiera la trabaja (RF-TAR-02/03).
        assertNotNull(buscar(jefe, t.get("id").asText()));
        JsonNode enProceso = mover(otroJefe, t, "EN_PROCESO").body();
        assertEquals("EN_PROCESO", enProceso.get("estado").asText());
        assertTrue(enProceso.get("completadaEn").isNull());
        // Con la versión vieja: alguien más la cambió.
        assertEquals(409, mover(jefe, t, "HECHA").status());

        JsonNode hecha = mover(jefe, enProceso, "HECHA").body();
        assertEquals("HECHA", hecha.get("estado").asText());
        assertEquals("Prueba JEFE_SISTEMAS", hecha.get("completadaPorNombre").asText());
        assertFalse(hecha.get("completadaEn").isNull());

        // Reabrirla borra quién la terminó.
        JsonNode reabierta = mover(secretaria, hecha, "POR_HACER").body();
        assertTrue(reabierta.get("completadaPorNombre").isNull());
    }

    @Test
    void unaPrivadaSoloLaVeQuienLaCreo() throws Exception {
        JsonNode t = crear(jefe, Map.of("titulo", "Revisar mi correo", "privada", true));
        String id = t.get("id").asText();
        assertNotNull(buscar(jefe, id));
        assertNull(buscar(otroJefe, id));
        assertNull(buscar(secretaria, id));
        // Para los demás no existe.
        assertEquals(404, mover(secretaria, t, "HECHA").status());
        assertEquals(404, llamar(delete("/api/tareas-equipo/" + id), otroJefe, null).status());
        // No se asigna a otra persona.
        assertEquals(400, llamar(post("/api/tareas-equipo"), jefe,
                Map.of("titulo", "x", "privada", true, "asignadaAId", idSecretaria.toString())).status());
    }

    @Test
    void soloSeAsignaALosJefesOALaSecretaria() throws Exception {
        assertEquals(400, llamar(post("/api/tareas-equipo"), jefe,
                Map.of("titulo", "Instalar", "asignadaAId", idInstalador.toString())).status());
        JsonNode personas = llamar(get("/api/tareas-equipo/personas"), secretaria, null).body();
        boolean hayInstalador = false;
        for (JsonNode p : personas) {
            hayInstalador |= p.get("rol").asText().equals("INSTALADOR");
        }
        assertFalse(hayInstalador);
        assertEquals(400, llamar(post("/api/tareas-equipo"), jefe, Map.of("titulo", "   ")).status());
    }

    @Test
    void losDemasRolesNoVenLaLista() throws Exception {
        assertEquals(403, llamar(get("/api/tareas-equipo"), instalador, null).status());
        assertEquals(403, llamar(get("/api/tareas-equipo"), marketing, null).status());
        assertEquals(403, llamar(post("/api/tareas-equipo"), instalador, Map.of("titulo", "x")).status());
    }

    @Test
    void seEditaYSoloElCreadorOUnJefeLaElimina() throws Exception {
        JsonNode t = crear(otroJefe, Map.of("titulo", "Pedir cotización de cable"));
        Map<String, Object> cambio = new HashMap<>();
        cambio.put("version", t.get("version").asLong());
        cambio.put("titulo", "Pedir cotización de cable UTP");
        cambio.put("descripcion", "Dos cajas");
        cambio.put("privada", false);
        Resp editada = llamar(put("/api/tareas-equipo/" + t.get("id").asText()), secretaria, cambio);
        assertEquals(200, editada.status(), String.valueOf(editada.body()));
        assertEquals("Dos cajas", editada.body().get("descripcion").asText());
        // Solo quien la creó la vuelve privada.
        cambio.put("version", editada.body().get("version").asLong());
        cambio.put("privada", true);
        assertEquals(403, llamar(put("/api/tareas-equipo/" + t.get("id").asText()), secretaria, cambio).status());

        assertEquals(403, llamar(delete("/api/tareas-equipo/" + t.get("id").asText()), secretaria, null).status());
        assertEquals(204, llamar(delete("/api/tareas-equipo/" + t.get("id").asText()), jefe, null).status());
        assertNull(buscar(otroJefe, t.get("id").asText()));
    }

    @Test
    void seLigaASoloUnRegistro() throws Exception {
        assertEquals(400, llamar(post("/api/tareas-equipo"), jefe, Map.of("titulo", "x",
                "leadId", UUID.randomUUID().toString(), "proyectoId", UUID.randomUUID().toString())).status());
        assertEquals(404, llamar(post("/api/tareas-equipo"), jefe, Map.of("titulo", "x",
                "cotizacionId", UUID.randomUUID().toString())).status());
    }
}
