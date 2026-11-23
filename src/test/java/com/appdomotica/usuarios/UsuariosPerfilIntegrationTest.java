package com.appdomotica.usuarios;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administración de cuentas (solo jefes) y la propia cuenta: contraseña, teléfono y foto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuariosPerfilIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> telefonos = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String telefono : telefonos) {
            usuarios.findByTelefono(telefono).ifPresent(u -> {
                jdbc.update("update usuarios set foto_id = null where id = ?", u.getId());
                jdbc.update("delete from archivos_adjuntos where subido_por = ?", u.getId());
                jdbc.update("delete from refresh_tokens where usuario_id = ?", u.getId());
                jdbc.update("delete from solicitudes_idempotentes where usuario_id = ?", u.getId());
                jdbc.update("delete from usuarios where id = ?", u.getId());
            });
            jdbc.update("delete from intentos_login where telefono = ?", telefono);
        }
    }

    private String telefonoNuevo() {
        String t = "9" + String.format("%07d", Math.abs(UUID.randomUUID().hashCode()) % 10_000_000);
        telefonos.add(t);
        return t;
    }

    private Usuario crear(RolUsuario rol) {
        Usuario u = new Usuario();
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setTelefono(telefonoNuevo());
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setActivo(true);
        return usuarios.save(u);
    }

    private MvcResult login(String telefono, String password) throws Exception {
        return mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", telefono, "password", password)))).andReturn();
    }

    private JsonNode sesion(String telefono, String password) throws Exception {
        MvcResult r = login(telefono, password);
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String bearer(Usuario u) throws Exception {
        return "Bearer " + sesion(u.getTelefono(), PASSWORD).get("token").asText();
    }

    private MvcResult enviar(MockHttpServletRequestBuilder req, String bearer, Object cuerpo) throws Exception {
        return mvc.perform(req.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo))).andReturn();
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    // --- Administración (jefes) ---

    @Test
    void jefeCreaEditaYDesactivaCuentas() throws Exception {
        String jefe = bearer(crear(RolUsuario.JEFE_SISTEMAS));
        String telefono = telefonoNuevo();

        MvcResult creado = enviar(post("/api/usuarios/admin"), jefe,
                Map.of("nombre", "  Nueva Persona ", "rol", "INSTALADOR", "telefono", telefono, "password", PASSWORD));
        assertEquals(201, creado.getResponse().getStatus());
        String id = cuerpo(creado).get("id").asText();
        assertEquals("Nueva Persona", cuerpo(creado).get("nombre").asText());
        sesion(telefono, PASSWORD);

        MvcResult editado = enviar(put("/api/usuarios/admin/" + id), jefe,
                Map.of("nombre", "Nueva Persona", "rol", "SECRETARIA", "telefono", telefono));
        assertEquals(200, editado.getResponse().getStatus());
        assertEquals("SECRETARIA", cuerpo(editado).get("rol").asText());

        MvcResult desactivado = enviar(patch("/api/usuarios/admin/" + id + "/estado"), jefe, Map.of("activo", false));
        assertEquals(200, desactivado.getResponse().getStatus());
        assertEquals(401, login(telefono, PASSWORD).getResponse().getStatus());
    }

    @Test
    void soloLosJefesAdministranCuentas() throws Exception {
        Usuario otro = crear(RolUsuario.INSTALADOR);
        for (RolUsuario rol : List.of(RolUsuario.SECRETARIA, RolUsuario.INGENIERO_INDUSTRIAL, RolUsuario.INSTALADOR,
                RolUsuario.MARKETING)) {
            String b = bearer(crear(rol));
            mvc.perform(get("/api/usuarios/admin").header("Authorization", b)).andExpect(status().isForbidden());
            assertEquals(403, enviar(post("/api/usuarios/admin"), b, Map.of("nombre", "X", "rol", "JEFE_SISTEMAS",
                    "telefono", telefonoNuevo(), "password", PASSWORD)).getResponse().getStatus());
            assertEquals(403, enviar(put("/api/usuarios/admin/" + otro.getId()), b, Map.of("nombre", "X",
                    "rol", "JEFE_SISTEMAS", "telefono", otro.getTelefono())).getResponse().getStatus());
            assertEquals(403, enviar(patch("/api/usuarios/admin/" + otro.getId() + "/estado"), b,
                    Map.of("activo", false)).getResponse().getStatus());
        }
    }

    @Test
    void validaTelefonoContrasenaYDuplicados() throws Exception {
        Usuario existente = crear(RolUsuario.INSTALADOR);
        String jefe = bearer(crear(RolUsuario.JEFE_ELECTROMECANICO));

        assertEquals(400, enviar(post("/api/usuarios/admin"), jefe, Map.of("nombre", "X", "rol", "INSTALADOR",
                "telefono", "123", "password", PASSWORD)).getResponse().getStatus());
        assertEquals(400, enviar(post("/api/usuarios/admin"), jefe, Map.of("nombre", "X", "rol", "INSTALADOR",
                "telefono", "8".repeat(60), "password", PASSWORD)).getResponse().getStatus());
        assertEquals(400, enviar(post("/api/usuarios/admin"), jefe, Map.of("nombre", "X", "rol", "INSTALADOR",
                "telefono", telefonoNuevo(), "password", "123456")).getResponse().getStatus());
        assertEquals(400, enviar(post("/api/usuarios/admin"), jefe, Map.of("nombre", "X", "rol", "INSTALADOR",
                "telefono", existente.getTelefono(), "password", PASSWORD)).getResponse().getStatus());
    }

    @Test
    void unJefeNoSeDesactivaNiSeQuitaElRol() throws Exception {
        Usuario yo = crear(RolUsuario.JEFE_SISTEMAS);
        String b = bearer(yo);
        assertEquals(400, enviar(patch("/api/usuarios/admin/" + yo.getId() + "/estado"), b, Map.of("activo", false))
                .getResponse().getStatus());
        assertEquals(400, enviar(put("/api/usuarios/admin/" + yo.getId()), b, Map.of("nombre", "Yo",
                "rol", "INSTALADOR", "telefono", yo.getTelefono())).getResponse().getStatus());
        assertTrue(usuarios.findById(yo.getId()).orElseThrow().getActivo());
    }

    // --- La propia cuenta ---

    @Test
    void cambiarContrasenaCierraLasDemasSesiones() throws Exception {
        Usuario yo = crear(RolUsuario.INSTALADOR);
        JsonNode otroTelefono = sesion(yo.getTelefono(), PASSWORD);
        String b = "Bearer " + sesion(yo.getTelefono(), PASSWORD).get("token").asText();

        assertEquals(400, enviar(put("/api/usuarios/me/password"), b,
                Map.of("actual", "otra-clave", "nueva", "clave-nueva-456")).getResponse().getStatus());
        assertEquals(400, enviar(put("/api/usuarios/me/password"), b,
                Map.of("actual", PASSWORD, "nueva", "corta")).getResponse().getStatus());

        MvcResult ok = enviar(put("/api/usuarios/me/password"), b, Map.of("actual", PASSWORD, "nueva", "clave-nueva-456"));
        assertEquals(200, ok.getResponse().getStatus());
        JsonNode nueva = cuerpo(ok);
        assertNotNull(nueva.get("token").asText());

        assertEquals(401, refrescar(otroTelefono.get("refreshToken").asText()));
        assertEquals(200, refrescar(nueva.get("refreshToken").asText()));
        assertEquals(401, login(yo.getTelefono(), PASSWORD).getResponse().getStatus());
        sesion(yo.getTelefono(), "clave-nueva-456");
    }

    @Test
    void cambiarTelefonoExigeContrasenaYEntregaSesionNueva() throws Exception {
        Usuario yo = crear(RolUsuario.SECRETARIA);
        Usuario otro = crear(RolUsuario.INSTALADOR);
        String viejo = bearer(yo);
        String telefono = telefonoNuevo();

        assertEquals(400, enviar(put("/api/usuarios/me/telefono"), viejo,
                Map.of("telefono", telefono, "password", "otra-clave")).getResponse().getStatus());
        assertEquals(400, enviar(put("/api/usuarios/me/telefono"), viejo,
                Map.of("telefono", otro.getTelefono(), "password", PASSWORD)).getResponse().getStatus());
        assertEquals(400, enviar(put("/api/usuarios/me/telefono"), viejo,
                Map.of("telefono", "12ab", "password", PASSWORD)).getResponse().getStatus());

        MvcResult ok = enviar(put("/api/usuarios/me/telefono"), viejo, Map.of("telefono", telefono, "password", PASSWORD));
        assertEquals(200, ok.getResponse().getStatus());
        assertEquals(telefono, cuerpo(ok).get("usuario").get("telefono").asText());

        String nuevo = "Bearer " + cuerpo(ok).get("token").asText();
        mvc.perform(get("/api/usuarios/me").header("Authorization", nuevo)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/me").header("Authorization", viejo)).andExpect(status().isUnauthorized());
        sesion(telefono, PASSWORD);
        assertEquals(401, login(yo.getTelefono(), PASSWORD).getResponse().getStatus());
    }

    @Test
    void fotoDePerfilSeReemplazaYSeQuita() throws Exception {
        Usuario yo = crear(RolUsuario.MARKETING);
        String b = bearer(yo);

        String primera = subirFoto(b, new byte[] {1, 2, 3});
        String segunda = subirFoto(b, new byte[] {4, 5, 6});
        assertNotEquals(primera, segunda);
        JsonNode me = cuerpo(mvc.perform(get("/api/usuarios/me").header("Authorization", b)).andReturn());
        assertEquals(segunda, me.get("fotoId").asText());
        assertNotNull(jdbc.queryForObject("select deleted_at from archivos_adjuntos where id = ?::uuid",
                java.time.OffsetDateTime.class, primera));
        // Cualquier compañero ve la foto vigente, aunque su rol no use la descarga de archivos.
        mvc.perform(get("/api/usuarios/" + yo.getId() + "/foto").header("Authorization", b))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/" + yo.getId() + "/foto")
                .header("Authorization", bearer(crear(RolUsuario.INSTALADOR)))).andExpect(status().isOk());

        MvcResult noImagen = mvc.perform(multipart("/api/usuarios/me/foto")
                .file(new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {7}))
                .header("Authorization", b)).andReturn();
        assertEquals(400, noImagen.getResponse().getStatus());

        JsonNode sinFoto = cuerpo(mvc.perform(delete("/api/usuarios/me/foto").header("Authorization", b)).andReturn());
        assertTrue(sinFoto.get("fotoId").isNull());
        mvc.perform(get("/api/usuarios/" + yo.getId() + "/foto").header("Authorization", b))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasRespuestasConTokensNoSeGuardanParaRepetir() throws Exception {
        Usuario yo = crear(RolUsuario.INSTALADOR);
        String b = bearer(yo);
        String actual = PASSWORD;
        // La segunda ruta va codificada a mano: Spring la atiende igual, así que tampoco se guarda.
        for (String ruta : List.of("/api/usuarios/me/password", "/api/usuarios/me/%70assword")) {
            String clave = UUID.randomUUID().toString();
            String nueva = "clave-" + UUID.randomUUID();
            MvcResult r = mvc.perform(put(URI.create(ruta)).header("Authorization", b).header("Idempotency-Key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("actual", actual, "nueva", nueva)))).andReturn();
            assertEquals(200, r.getResponse().getStatus(), ruta);
            b = "Bearer " + cuerpo(r).get("token").asText();
            actual = nueva;
            Integer filas = jdbc.queryForObject(
                    "select count(*) from solicitudes_idempotentes where clave = ?", Integer.class, clave);
            assertEquals(0, filas, ruta);
        }
    }

    private String subirFoto(String bearer, byte[] bytes) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/usuarios/me/foto")
                .file(new MockMultipartFile("file", "foto.png", "image/png", bytes))
                .header("Authorization", bearer)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return cuerpo(r).get("fotoId").asText();
    }

    private int refrescar(String refreshToken) throws Exception {
        return mvc.perform(post("/api/usuarios/refresh-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andReturn().getResponse().getStatus();
    }
}
