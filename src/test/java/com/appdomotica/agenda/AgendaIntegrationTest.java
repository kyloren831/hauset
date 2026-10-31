package com.appdomotica.agenda;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Agenda compartida, aviso de cruces y visibilidad por rol (RF-AGE-01..05). */
@SpringBootTest
@AutoConfigureMockMvc
class AgendaIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private String secretaria, jefe, instalador, industrial, marketing;
    private UUID instaladorId, ultimoId;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        instalador = token(RolUsuario.INSTALADOR);
        instaladorId = ultimoId;
        industrial = token(RolUsuario.INGENIERO_INDUSTRIAL);
        marketing = token(RolUsuario.MARKETING);
    }

    // ---------- utilidades ----------

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
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = texto.isBlank() || !texto.trim().startsWith("{") && !texto.trim().startsWith("[")
                ? null : json.readTree(texto);
        return new Resp(r.getResponse().getStatus(), body);
    }

    /** Proyecto con 2 tareas: una cámara en la ubicación 1 y una cerradura en la 2. */
    private UUID proyecto() throws Exception {
        ProductoCatalogo cam = new ProductoCatalogo();
        cam.setNombre("Cámara visitas " + SECUENCIA.incrementAndGet());
        cam.setCategoria(CategoriaProducto.SEGURIDAD);
        UUID camara = productos.save(cam).getId();
        ProductoCatalogo cer = new ProductoCatalogo();
        cer.setNombre("Cerradura visitas " + SECUENCIA.incrementAndGet());
        cer.setCategoria(CategoriaProducto.CONTROL_ACCESO);
        UUID cerradura = productos.save(cer).getId();
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor visitas " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        UUID proveedor = proveedores.save(prov).getId();

        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Familia Agenda " + SECUENCIA.incrementAndGet(), "telefono", "8888-4321", "direccion", "Palmares")));
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Portón", "items", List.of(Map.of("productoId", camara, "cantidad", 1))),
                        Map.of("nota", "Puerta principal", "items", List.of(Map.of("productoId", cerradura, "cantidad", 1))))));
        assertEquals(201, informe.status(), String.valueOf(informe.body()));
        JsonNode cot = llamar(post("/api/cotizaciones"), secretaria,
                Map.of("informeId", informe.body().get("id").asText())).body();
        String cid = cot.get("id").asText();
        cot = llamar(put("/api/cotizaciones/" + cid + "/items"), secretaria, Map.of("version", cot.get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", camara, "proveedorId", proveedor, "cantidad", 1, "precioNeto", 40000),
                        Map.of("productoId", cerradura, "proveedorId", proveedor, "cantidad", 1, "precioNeto", 90000)))).body();
        cot = llamar(put("/api/cotizaciones/" + cid + "/margenes"), jefe,
                Map.of("version", cot.get("version").asLong(), "margenGeneral", 25)).body();
        cot = llamar(post("/api/cotizaciones/" + cid + "/enviar"), secretaria, Map.of("version", cot.get("version").asLong())).body();
        Resp aceptada = llamar(post("/api/cotizaciones/" + cid + "/aceptar"), secretaria,
                Map.of("version", cot.get("version").asLong()));
        assertEquals(200, aceptada.status(), String.valueOf(aceptada.body()));
        Resp iniciada = llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria,
                Map.of("version", aceptada.body().get("version").asLong()));
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        return UUID.fromString(iniciada.body().get("proyectoId").asText());
    }

    private JsonNode detalle(UUID proyecto) throws Exception {
        return llamar(get("/api/proyectos/" + proyecto), jefe, null).body();
    }

    private static final ZoneId CR = ZoneId.of("America/Costa_Rica");
    private static final LocalDate MANANA = LocalDate.now(CR).plusDays(1);

    private static String a(int hora, int minuto) {
        return MANANA.atTime(LocalTime.of(hora, minuto)).atZone(CR).toOffsetDateTime().toString();
    }

    private Resp agendar(UUID proyecto, UUID persona, String cuando, Integer minutos, boolean confirmar) throws Exception {
        Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("asignadoAId", persona);
        cuerpo.put("fechaHora", cuando);
        cuerpo.put("confirmarCruce", confirmar);
        if (minutos != null) {
            cuerpo.put("duracionMinutos", minutos);
        }
        return llamar(post("/api/proyectos/" + proyecto + "/visitas"), jefe, cuerpo);
    }

    private JsonNode agendaDeManana(String bearer, String extra) throws Exception {
        Resp r = llamar(get("/api/agenda?desde=" + MANANA + "&hasta=" + MANANA + extra), bearer, null);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    private static JsonNode visitaDe(JsonNode agenda, UUID proyecto) {
        for (JsonNode v : agenda.get("visitas")) {
            if (v.get("proyectoId").asText().equals(proyecto.toString())) {
                return v;
            }
        }
        return null;
    }

    @Test
    void avisaCrucesSinBloquearYLaAgendaLosMarca() throws Exception {
        UUID p1 = proyecto(), p2 = proyecto(), p3 = proyecto(), p4 = proyecto();

        // 9:00 a 11:00 (duración por omisión: 2 horas).
        Resp v1 = agendar(p1, instaladorId, a(9, 0), null, false);
        assertEquals(200, v1.status(), String.valueOf(v1.body()));
        assertEquals(120, v1.body().get("visitas").get(0).get("duracionMinutos").asInt());

        // 10:00 se cruza: aviso con el detalle y no se guarda nada.
        Resp cruce = agendar(p2, instaladorId, a(10, 0), 60, false);
        assertEquals(409, cruce.status());
        assertEquals("CRUCE_AGENDA", cruce.body().get("codigo").asText());
        assertEquals(p1.toString(), cruce.body().get("cruces").get(0).get("proyectoId").asText());
        assertTrue(cruce.body().get("message").asText().contains("ya tiene una visita"));
        assertEquals(0, detalle(p2).get("visitas").size());

        // 11:00 solo toca el borde: no es cruce.
        assertEquals(200, agendar(p3, instaladorId, a(11, 0), 60, false).status());

        // Otra persona a la misma hora: tampoco.
        assertEquals(200, agendar(p4, ultimoIdDe(RolUsuario.INSTALADOR), a(9, 0), null, false).status());

        // Quien agenda decide mantenerlo: se guarda y la agenda marca a las dos.
        assertEquals(200, agendar(p2, instaladorId, a(10, 0), 60, true).status());
        JsonNode agenda = agendaDeManana(secretaria, "");
        assertTrue(agenda.get("todaLaAgenda").asBoolean());
        assertTrue(visitaDe(agenda, p1).get("conCruce").asBoolean());
        assertTrue(visitaDe(agenda, p2).get("conCruce").asBoolean());
        assertFalse(visitaDe(agenda, p3).get("conCruce").asBoolean());
        assertFalse(visitaDe(agenda, p4).get("conCruce").asBoolean());
        assertEquals("Prueba INSTALADOR", visitaDe(agenda, p1).get("asignadoA").asText());
        assertNotNull(visitaDe(agenda, p1).get("clienteNombre").asText());

        // Filtro por persona.
        JsonNode soloOtro = agendaDeManana(jefe, "&personaId=" + instaladorId);
        assertNotNull(visitaDe(soloOtro, p1));
        assertNull(visitaDe(soloOtro, p4));
    }

    @Test
    void moverUnaVisitaNoChocaConsigoMismaPeroSiConLaNuevaPersona() throws Exception {
        UUID p1 = proyecto(), p2 = proyecto();
        UUID otro = ultimoIdDe(RolUsuario.INSTALADOR);
        JsonNode v = agendar(p1, instaladorId, a(14, 0), null, false).body().get("visitas").get(0);
        assertEquals(200, agendar(p2, otro, a(14, 0), null, false).status());

        // Correrla media hora: se cruzaría con ella misma, pero no cuenta.
        Resp movida = llamar(put("/api/proyectos/visitas/" + v.get("id").asText() + "/agenda"), jefe, Map.of(
                "version", v.get("version").asLong(), "asignadoAId", instaladorId, "fechaHora", a(14, 30)));
        assertEquals(200, movida.status(), String.valueOf(movida.body()));
        v = movida.body().get("visitas").get(0);
        assertEquals(120, v.get("duracionMinutos").asInt());

        // Pasarla a la otra persona, que ya tiene visita a esa hora: aviso.
        Resp cambio = llamar(put("/api/proyectos/visitas/" + v.get("id").asText() + "/agenda"), jefe, Map.of(
                "version", v.get("version").asLong(), "asignadoAId", otro, "fechaHora", a(14, 30),
                "duracionMinutos", 45));
        assertEquals(409, cambio.status());
        assertEquals("CRUCE_AGENDA", cambio.body().get("codigo").asText());
    }

    @Test
    void elInstaladorSoloVeSusVisitasYMarketingNada() throws Exception {
        UUID p1 = proyecto(), p2 = proyecto();
        UUID otro = ultimoIdDe(RolUsuario.INSTALADOR);
        agendar(p1, instaladorId, a(8, 0), null, false);
        agendar(p2, otro, a(8, 0), null, false);

        JsonNode propia = agendaDeManana(instalador, "&personaId=" + otro);
        assertFalse(propia.get("todaLaAgenda").asBoolean());
        assertNotNull(visitaDe(propia, p1));
        assertNull(visitaDe(propia, p2));
        assertEquals(0, propia.get("porAgendar").size());
        for (JsonNode x : propia.get("visitas")) {
            assertEquals(instaladorId.toString(), x.get("asignadoAId").asText());
        }

        assertEquals(403, llamar(get("/api/agenda"), marketing, null).status());
        // La secretaria ve, pero no agenda (RF-AGE-02).
        assertEquals(403, llamar(post("/api/proyectos/" + p1 + "/visitas"), secretaria,
                Map.of("asignadoAId", instaladorId, "fechaHora", a(16, 0))).status());
    }

    @Test
    void losRetornosEsperanEnLaBandejaYLaDuracionSeValida() throws Exception {
        UUID p = proyecto();
        llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null);
        assertEquals(200, llamar(post("/api/proyectos/" + p + "/retorno"), instalador,
                Map.of("motivo", "Falta un tomacorriente")).status());

        JsonNode agenda = llamar(get("/api/agenda"), secretaria, null).body();
        JsonNode pendiente = null;
        for (JsonNode x : agenda.get("porAgendar")) {
            if (x.get("proyectoId").asText().equals(p.toString())) {
                pendiente = x;
            }
        }
        assertNotNull(pendiente, "El retorno debe estar por agendar");
        assertEquals("PENDIENTE_AGENDAR", pendiente.get("estado").asText());
        assertEquals("Falta un tomacorriente", pendiente.get("motivo").asText());
        assertTrue(pendiente.get("inicio").isNull());

        assertEquals(400, agendar(p, instaladorId, a(9, 0), 10, false).status());
        assertEquals(400, agendar(p, instaladorId, a(9, 0), 800, false).status());
        assertEquals(400, llamar(get("/api/agenda?desde=" + MANANA + "&hasta=" + MANANA.minusDays(1)), jefe, null).status());
        assertEquals(400, llamar(get("/api/agenda?desde=" + MANANA + "&hasta=" + MANANA.plusDays(70)), jefe, null).status());
    }

    /** Crea otra persona del rol y devuelve su id. */
    private UUID ultimoIdDe(RolUsuario rol) throws Exception {
        token(rol);
        return ultimoId;
    }
}
