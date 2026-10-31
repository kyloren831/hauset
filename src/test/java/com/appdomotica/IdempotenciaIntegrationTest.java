package com.appdomotica;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Reintentos con la misma Idempotency-Key no repiten la operación. */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotenciaIntegrationTest {
    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private String secretaria, otraSecretaria, industrial;
    private UUID secretariaId;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        secretariaId = ultimoId;
        otraSecretaria = token(RolUsuario.SECRETARIA);
        industrial = token(RolUsuario.INGENIERO_INDUSTRIAL);
    }

    private MvcResult crearLead(String bearer, String clave, String nombre) throws Exception {
        var req = post("/api/leads").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cliente", Map.of("nombre", nombre))));
        if (clave != null) {
            req.header("Idempotency-Key", clave);
        }
        return mvc.perform(req).andReturn();
    }

    private int leadsCon(String nombre) {
        return jdbc.queryForObject("select count(*) from leads l join clientes c on c.id = l.cliente_id where c.nombre = ?",
                Integer.class, nombre);
    }

    @Test
    void elReintentoDevuelveLaMismaRespuestaSinDuplicar() throws Exception {
        String clave = UUID.randomUUID().toString();
        String nombre = "Idem " + clave;
        MvcResult primera = crearLead(secretaria, clave, nombre);
        MvcResult segunda = crearLead(secretaria, clave, nombre);
        assertEquals(201, primera.getResponse().getStatus());
        assertEquals(201, segunda.getResponse().getStatus());
        assertEquals("true", segunda.getResponse().getHeader("Idempotent-Replayed"));
        assertNull(primera.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals(id(primera), id(segunda));
        assertEquals(1, leadsCon(nombre));

        // Sin clave no hay protección: se crean dos.
        String otro = "Sin clave " + clave;
        crearLead(secretaria, null, otro);
        crearLead(secretaria, null, otro);
        assertEquals(2, leadsCon(otro));
    }

    @Test
    void laClaveEsPorUsuarioYPorOperacion() throws Exception {
        String clave = UUID.randomUUID().toString();
        String nombre = "Por usuario " + clave;
        crearLead(secretaria, clave, nombre);
        // Otro usuario con la misma clave: es otra operación.
        assertEquals(201, crearLead(otraSecretaria, clave, nombre).getResponse().getStatus());
        assertEquals(2, leadsCon(nombre));
        // Misma clave para otra ruta: rechazada.
        MvcResult otraRuta = mvc.perform(post("/api/catalogo/productos").header("Authorization", secretaria)
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"x\",\"categoria\":\"REDES\"}")).andReturn();
        assertEquals(422, otraRuta.getResponse().getStatus());
    }

    @Test
    void siLaPrimeraSigueEnProcesoLaRepeticionEspera() throws Exception {
        String clave = UUID.randomUUID().toString();
        jdbc.update("insert into solicitudes_idempotentes (usuario_id, clave, huella, estado) values (?, ?, ?, 'EN_PROCESO')",
                secretariaId, clave, "POST /api/leads");
        MvcResult r = crearLead(secretaria, clave, "En proceso " + clave);
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("true", r.getResponse().getHeader("Idempotency-In-Progress"));
        // Abandonada (el servidor se cayó a mitad): se vuelve a ejecutar.
        jdbc.update("update solicitudes_idempotentes set creada_en = now() - interval '10 minutes' "
                + "where usuario_id = ? and clave = ?", secretariaId, clave);
        assertEquals(201, crearLead(secretaria, clave, "En proceso " + clave).getResponse().getStatus());
    }

    @Test
    void laSubidaDeUnaFotoNoSeDuplica() throws Exception {
        String producto = json.readTree(mvc.perform(post("/api/catalogo/productos").header("Authorization", industrial)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Router idem " + UUID.randomUUID() + "\",\"categoria\":\"REDES\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();
        String clave = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            // Bytes distintos: sin idempotencia serían dos fotos.
            MvcResult r = mvc.perform(multipart("/api/catalogo/productos/" + producto + "/fotos")
                    .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1, (byte) i}))
                    .header("Authorization", industrial).header("Idempotency-Key", clave)).andReturn();
            assertEquals(200, r.getResponse().getStatus());
            assertEquals(1, json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .get("fotoIds").size());
        }
    }

    // ---------- utilidades ----------

    private UUID ultimoId;

    private String id(MvcResult r) throws Exception {
        JsonNode n = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return n.get("id").asText();
    }

    private String token(RolUsuario rol) throws Exception {
        String telefono = "5" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
        Usuario u = new Usuario();
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setTelefono(telefono);
        u.setPasswordHash(encoder.encode("clave-de-prueba-123"));
        u.setActivo(true);
        ultimoId = usuarios.save(u).getId();
        MvcResult r = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", telefono, "password", "clave-de-prueba-123"))))
                .andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }
}
