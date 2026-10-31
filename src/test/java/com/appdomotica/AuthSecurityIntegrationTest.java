package com.appdomotica;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.IntentoLoginRepository;
import com.appdomotica.usuarios.repository.RefreshTokenRepository;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticación y autorización: login, rotación de refresh tokens, revocación,
 * usuarios desactivados, límite de intentos y permisos por rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private IntentoLoginRepository intentos;
    @Autowired private PasswordEncoder encoder;

    private String telefono;

    @BeforeEach
    void setUp() {
        telefono = "9" + String.format("%07d", Math.abs(UUID.randomUUID().hashCode()) % 10_000_000);
    }

    @AfterEach
    void limpiar() {
        usuarios.findByTelefono(telefono).ifPresent(u -> {
            refreshTokens.findAll().stream()
                    .filter(t -> t.getUsuario().getId().equals(u.getId()))
                    .forEach(refreshTokens::delete);
            usuarios.delete(u);
        });
        intentos.findAll().stream().filter(i -> i.getTelefono().equals(telefono)).forEach(intentos::delete);
    }

    private Usuario crearUsuario(RolUsuario rol, boolean activo) {
        Usuario u = new Usuario();
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setTelefono(telefono);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setActivo(activo);
        return usuarios.save(u);
    }

    private MvcResult login(String password) throws Exception {
        return mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("telefono", telefono, "password", password))))
                .andReturn();
    }

    private JsonNode loginOk() throws Exception {
        MvcResult r = login(PASSWORD);
        assertEquals(200, r.getResponse().getStatus());
        return json.readTree(r.getResponse().getContentAsString());
    }

    private int refrescar(String refreshToken) throws Exception {
        return mvc.perform(post("/api/usuarios/refresh-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void loginEntregaTokenYPermiteConsultarElPerfil() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        JsonNode r = loginOk();
        mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + r.get("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void credencialesIncorrectasDanElMismoMensajeParaUsuarioInexistenteYContrasenaMala() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        String mala = login("otra-clave").getResponse().getContentAsString();
        String inexistente = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"00000000\",\"password\":\"x\"}")).andReturn().getResponse().getContentAsString();
        assertEquals(401, login("otra-clave").getResponse().getStatus());
        assertEquals(mala, inexistente);
    }

    @Test
    void sinTokenOTokenInvalidoResponde401() throws Exception {
        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/catalogo/productos").header("Authorization", "Bearer basura.invalida.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioDesactivadoPierdeAccesoDeInmediato() throws Exception {
        Usuario u = crearUsuario(RolUsuario.INSTALADOR, true);
        JsonNode r = loginOk();
        String bearer = "Bearer " + r.get("token").asText();
        mvc.perform(get("/api/usuarios/me").header("Authorization", bearer)).andExpect(status().isOk());

        u.setActivo(false);
        usuarios.save(u);
        mvc.perform(get("/api/usuarios/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        assertEquals(401, login(PASSWORD).getResponse().getStatus());
    }

    @Test
    void refreshRotaElTokenYReutilizarUnoViejoRevocaLaFamilia() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        String primero = loginOk().get("refreshToken").asText();

        MvcResult r = mvc.perform(post("/api/usuarios/refresh-token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("refreshToken", primero)))).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String segundo = json.readTree(r.getResponse().getContentAsString()).get("refreshToken").asText();
        assertNotEquals(primero, segundo);

        // Reutilizar el primero (ya rotado) es señal de robo: se rechaza y se revoca toda la familia.
        assertEquals(401, refrescar(primero));
        assertEquals(401, refrescar(segundo));
    }

    @Test
    void logoutRevocaLasSesionesAbiertas() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        JsonNode r = loginOk();
        mvc.perform(post("/api/usuarios/logout").header("Authorization", "Bearer " + r.get("token").asText()))
                .andExpect(status().isNoContent());
        assertEquals(401, refrescar(r.get("refreshToken").asText()));
    }

    @Test
    void desactivarAlUsuarioRevocaSuRefreshToken() throws Exception {
        Usuario u = crearUsuario(RolUsuario.INSTALADOR, true);
        String refresh = loginOk().get("refreshToken").asText();
        u.setActivo(false);
        usuarios.save(u);
        assertEquals(401, refrescar(refresh));
    }

    @Test
    void cincoFallosBloqueanElLoginAunqueLaClaveSeaCorrecta() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login("incorrecta").getResponse().getStatus());
        }
        MvcResult r = login(PASSWORD);
        assertEquals(429, r.getResponse().getStatus());
        assertEquals("900", r.getResponse().getHeader("Retry-After"));
    }

    @Test
    void permisosPorRolEnCatalogoYArchivos() throws Exception {
        crearUsuario(RolUsuario.INSTALADOR, true);
        String bearer = "Bearer " + loginOk().get("token").asText();
        // El instalador consulta el catálogo pero no lo modifica.
        mvc.perform(get("/api/catalogo/productos").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/catalogo/productos").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Camara\",\"categoria\":\"SEGURIDAD\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/admin").header("Authorization", bearer)).andExpect(status().isForbidden());
        // Los precios netos de proveedor quedan fuera del alcance del instalador.
        mvc.perform(get("/api/catalogo/precios/producto/" + UUID.randomUUID() + "/ultimos").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void marketingNoPuedeUsarArchivos() throws Exception {
        crearUsuario(RolUsuario.MARKETING, true);
        String bearer = "Bearer " + loginOk().get("token").asText();
        mvc.perform(get("/api/v1/archivos/entidad/CHECKLIST_TAREA/" + UUID.randomUUID()).header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void elEndpointDePruebaYaNoExisteYSoloLaSaludEsPublica() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
