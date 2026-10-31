package com.appdomotica.proyectos;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Visitas con su propio avance, "requiere retorno" con agenda de jefes, cierre con firma de conformidad y
 * reporte de entrega en PDF (RF-PRY-11/12/13/15, RN-07, RN-09).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisitasCierreIntegrationTest {

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
                Map.of("cliente", Map.of("nombre", "Familia Visitas", "telefono", "8888-4321", "direccion", "Palmares")));
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

    private static byte[] imagen(String formato, Color color) throws Exception {
        BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 320, 240);
        g.setColor(Color.BLACK);
        g.drawLine(20, 200, 300, 40);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return out.toByteArray();
    }

    private void foto(String tarea, String momento) throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "f.jpg", "image/jpeg",
                imagen("jpg", momento.equals("ANTES") ? Color.ORANGE : Color.CYAN));
        assertEquals(200, mvc.perform(multipart("/api/proyectos/tareas/" + tarea + "/fotos/" + momento).file(f)
                .header("Authorization", instalador)).andReturn().getResponse().getStatus());
    }

    private Resp estado(JsonNode tarea, String estado, String notas, OffsetDateTime cuando) throws Exception {
        Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of("version", tarea.get("version").asLong(), "estado", estado));
        if (notas != null) {
            cuerpo.put("notas", notas);
        }
        if (cuando != null) {
            cuerpo.put("ocurridoEn", cuando.toString());
        }
        return llamar(put("/api/proyectos/tareas/" + tarea.get("id").asText() + "/estado"), instalador, cuerpo);
    }

    private JsonNode tarea(UUID proyecto, int i) throws Exception {
        return detalle(proyecto).get("tareas").get(i);
    }

    private Resp cerrar(UUID proyecto, String nombre, MockMultipartFile firma, String clave) throws Exception {
        var req = multipart("/api/proyectos/" + proyecto + "/cierre").file(firma).param("nombre", nombre)
                .header("Authorization", instalador);
        if (clave != null) {
            req.header("Idempotency-Key", clave);
        }
        MvcResult r = mvc.perform(req).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Resp(r.getResponse().getStatus(), texto.startsWith("{") ? json.readTree(texto) : null);
    }

    private MockMultipartFile firma() throws Exception {
        return new MockMultipartFile("firma", "firma.png", "image/png", imagen("png", Color.WHITE));
    }

    // ---------- pruebas ----------

    @Test
    void recorridoConRetornoFirmaYReporte() throws Exception {
        UUID p = proyecto();
        JsonNode d = detalle(p);
        assertEquals("COTIZADO", d.get("estado").asText());
        assertEquals(0, d.get("visitas").size());
        assertFalse(d.get("reporteDisponible").asBoolean());

        // Completar la primera tarea sin haber iniciado visita: se inicia sola y el proyecto pasa a instalación.
        String t1 = tarea(p, 0).get("id").asText();
        foto(t1, "ANTES");
        foto(t1, "DESPUES");
        Resp hecha = estado(tarea(p, 0), "COMPLETADA", null, null);
        assertEquals(200, hecha.status(), String.valueOf(hecha.body()));
        d = hecha.body();
        assertEquals("EN_INSTALACION", d.get("estado").asText());
        assertEquals(1, d.get("visitas").size());
        JsonNode v1 = d.get("visitas").get(0);
        assertEquals("EN_CURSO", v1.get("estado").asText());
        assertEquals(1, v1.get("tareasHechas").asInt());
        assertEquals(instaladorId.toString(), v1.get("asignadoAId").asText());
        assertEquals(1, d.get("tareas").get(0).get("visitaNumero").asInt());

        // Iniciar con una visita en curso de hoy no crea otra.
        assertEquals(1, llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null)
                .body().get("visitas").size());

        // Requiere retorno: motivo obligatorio; la visita termina y queda otra esperando agenda (RN-09).
        assertEquals(400, llamar(post("/api/proyectos/" + p + "/retorno"), instalador, Map.of("motivo", " ")).status());
        Resp retorno = llamar(post("/api/proyectos/" + p + "/retorno"), instalador,
                Map.of("motivo", "Falta cable UTP para la cerradura"));
        assertEquals(200, retorno.status(), String.valueOf(retorno.body()));
        d = retorno.body();
        assertEquals("REQUIERE_RETORNO", d.get("estado").asText());
        assertEquals("REQUIERE_RETORNO", d.get("visitas").get(0).get("estado").asText());
        assertEquals("Falta cable UTP para la cerradura", d.get("visitas").get(0).get("motivoRetorno").asText());
        JsonNode v2 = d.get("visitas").get(1);
        assertEquals("PENDIENTE_AGENDAR", v2.get("estado").asText());
        assertTrue(v2.get("fechaHora").isNull());
        assertEquals(409, llamar(post("/api/proyectos/" + p + "/retorno"), instalador, Map.of("motivo", "Otra")).status());
        JsonNode resumen = buscar(llamar(get("/api/proyectos"), instalador, null).body(), p);
        assertTrue(resumen.get("retornoPendiente").asBoolean());

        // Solo jefes e ingeniero industrial agendan (RF-AGE-02).
        OffsetDateTime manana = OffsetDateTime.now().plusDays(1);
        Map<String, Object> agenda = Map.of("version", v2.get("version").asLong(), "asignadoAId", instaladorId,
                "fechaHora", manana.toString());
        assertEquals(403, llamar(put("/api/proyectos/visitas/" + v2.get("id").asText() + "/agenda"), instalador, agenda).status());
        assertEquals(403, llamar(get("/api/proyectos/personal"), instalador, null).status());
        JsonNode personal = llamar(get("/api/proyectos/personal"), industrial, null).body();
        assertTrue(personal.findValuesAsText("id").contains(instaladorId.toString()));
        assertFalse(personal.findValuesAsText("rol").contains("MARKETING"));
        assertEquals(400, llamar(put("/api/proyectos/visitas/" + v2.get("id").asText() + "/agenda"), industrial,
                Map.of("version", v2.get("version").asLong(), "asignadoAId", instaladorId,
                        "fechaHora", OffsetDateTime.now().minusDays(2).toString())).status());
        Resp agendada = llamar(put("/api/proyectos/visitas/" + v2.get("id").asText() + "/agenda"), industrial, agenda);
        assertEquals(200, agendada.status(), String.valueOf(agendada.body()));
        assertEquals("AGENDADA", agendada.body().get("visitas").get(1).get("estado").asText());
        resumen = buscar(llamar(get("/api/proyectos"), instalador, null).body(), p);
        assertFalse(resumen.get("retornoPendiente").asBoolean());
        assertFalse(resumen.get("proximaVisita").isNull());

        // Sin todas las tareas no se cierra (RN-07).
        Resp temprano = cerrar(p, "Ana Cliente", firma(), null);
        assertEquals(409, temprano.status());
        assertTrue(temprano.body().get("message").asText().contains("falta 1 tarea"), temprano.body().toString());

        // El equipo vuelve antes de lo agendado: la visita que inicia es el retorno, no una nueva.
        d = llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null).body();
        assertEquals("EN_INSTALACION", d.get("estado").asText());
        assertEquals(2, d.get("visitas").size());
        assertEquals("EN_CURSO", d.get("visitas").get(1).get("estado").asText());

        Resp na = estado(tarea(p, 1), "NO_APLICA", "El cliente ya tenía cerradura", null);
        assertEquals(200, na.status(), String.valueOf(na.body()));
        assertEquals(2, na.body().get("tareas").get(1).get("visitaNumero").asInt());

        // La firma exige nombre e imagen.
        assertEquals(400, cerrar(p, " ", firma(), null).status());
        assertEquals(400, cerrar(p, "Ana Cliente",
                new MockMultipartFile("firma", "f.txt", "text/plain", "hola".getBytes()), null).status());

        Resp cerrado = cerrar(p, "Ana Cliente", firma(), "cierre-" + p);
        assertEquals(200, cerrado.status(), String.valueOf(cerrado.body()));
        d = cerrado.body();
        assertEquals("CERRADO", d.get("estado").asText());
        assertEquals("Ana Cliente", d.get("firma").get("nombre").asText());
        assertEquals(2, d.get("firma").get("visitaNumero").asInt());
        assertTrue(d.get("reporteDisponible").asBoolean());
        assertEquals("COMPLETADA", d.get("visitas").get(1).get("estado").asText());
        assertEquals(1, d.get("visitas").get(1).get("tareasHechas").asInt());
        assertTrue(d.get("eventos").findValuesAsText("tipo").contains("PROYECTO_CERRADO"));

        // Reenviar el mismo cierre (señal cortada) devuelve la misma respuesta, sin error.
        Resp repetido = cerrar(p, "Ana Cliente", firma(), "cierre-" + p);
        assertEquals(200, repetido.status());
        assertEquals("CERRADO", repetido.body().get("estado").asText());

        // Cerrado: nada más se modifica.
        assertEquals(409, cerrar(p, "Ana Cliente", firma(), null).status());
        assertEquals(409, llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null).status());
        assertEquals(409, estado(tarea(p, 1), "PENDIENTE", null, null).status());

        // Reporte guardado (RF-PRY-15) y firma inmutable.
        MvcResult pdf = mvc.perform(get("/api/proyectos/" + p + "/reporte").header("Authorization", secretaria)).andReturn();
        assertEquals(200, pdf.getResponse().getStatus());
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF"));
        assertTrue(bytes.length > 5_000, "lleva las fotos y la firma");
        assertEquals(1, jdbc.queryForObject("select count(*) from reportes_entrega where proyecto_id = ?", Integer.class, p));
        assertEquals(1, jdbc.queryForObject("select count(*) from firmas_conformidad f join visitas v on v.id = f.visita_id "
                + "where v.proyecto_id = ?", Integer.class, p));
        assertThrows(Exception.class, () -> jdbc.update("update firmas_conformidad set firmado_por_cliente = 'X' "
                + "where visita_id in (select id from visitas where proyecto_id = ?)", p));
        assertEquals(403, mvc.perform(get("/api/proyectos/" + p + "/reporte").header("Authorization", marketing))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void laVistaPreviaDelReporteSeDescargaAntesDeCerrar() throws Exception {
        UUID p = proyecto();
        MvcResult pdf = mvc.perform(get("/api/proyectos/" + p + "/reporte").header("Authorization", instalador)).andReturn();
        assertEquals(200, pdf.getResponse().getStatus());
        assertTrue(pdf.getResponse().getHeader("Content-Disposition").contains("vista-previa"));
        assertEquals(0, jdbc.queryForObject("select count(*) from reportes_entrega where proyecto_id = ?", Integer.class, p));
    }

    @Test
    void laHoraDelTelefonoCuentaSiEsRazonable() throws Exception {
        UUID p = proyecto();
        OffsetDateTime hace2h = OffsetDateTime.now().minusHours(2).withNano(0);
        JsonNode d = estado(tarea(p, 1), "NO_APLICA", "No se instala", hace2h).body();
        OffsetDateTime registrada = OffsetDateTime.parse(d.get("tareas").get(1).get("completadaEn").asText());
        assertEquals(hace2h.toInstant(), registrada.toInstant());
        assertEquals(hace2h.toInstant(), OffsetDateTime.parse(d.get("visitas").get(0).get("iniciadaEn").asText()).toInstant());

        // Una hora en el futuro no se cree: cuenta la del servidor.
        d = estado(tarea(p, 1), "PENDIENTE", null, null).body();
        d = estado(d.get("tareas").get(1), "NO_APLICA", "No se instala", OffsetDateTime.now().plusDays(3)).body();
        registrada = OffsetDateTime.parse(d.get("tareas").get(1).get("completadaEn").asText());
        assertTrue(Duration.between(registrada, OffsetDateTime.now()).abs().toMinutes() < 2);
    }

    @Test
    void unaVisitaQueQuedoAbiertaDeOtroDiaSeCierraSola() throws Exception {
        UUID p = proyecto();
        llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null);
        jdbc.update("update visitas set iniciada_en = iniciada_en - interval '2 days' where proyecto_id = ?", p);
        JsonNode d = llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null).body();
        assertEquals(2, d.get("visitas").size());
        assertEquals("COMPLETADA", d.get("visitas").get(0).get("estado").asText());
        assertEquals("EN_CURSO", d.get("visitas").get(1).get("estado").asText());
        assertTrue(d.get("eventos").findValuesAsText("detalle").stream().anyMatch(x -> x.contains("se cerró sola")));

        // Terminar deja el proyecto sin visita en curso; terminar otra vez no hace nada.
        d = llamar(post("/api/proyectos/" + p + "/visitas/terminar"), instalador, null).body();
        assertEquals("COMPLETADA", d.get("visitas").get(1).get("estado").asText());
        assertEquals(200, llamar(post("/api/proyectos/" + p + "/visitas/terminar"), instalador, null).status());
    }

    @Test
    void cancelarElRetornoDevuelveElProyectoAInstalacion() throws Exception {
        UUID p = proyecto();
        llamar(post("/api/proyectos/" + p + "/visitas/iniciar"), instalador, null);
        JsonNode d = llamar(post("/api/proyectos/" + p + "/retorno"), instalador, Map.of("motivo", "Cliente ausente")).body();
        JsonNode pendiente = d.get("visitas").get(1);
        assertEquals(409, llamar(post("/api/proyectos/visitas/" + pendiente.get("id").asText() + "/cancelar"), jefe,
                Map.of("version", pendiente.get("version").asLong() + 5)).status());
        d = llamar(post("/api/proyectos/visitas/" + pendiente.get("id").asText() + "/cancelar"), jefe,
                Map.of("version", pendiente.get("version").asLong())).body();
        assertEquals("EN_INSTALACION", d.get("estado").asText());
        assertEquals("CANCELADA", d.get("visitas").get(1).get("estado").asText());

        // Un jefe agenda una visita nueva directamente.
        Resp nueva = llamar(post("/api/proyectos/" + p + "/visitas"), jefe, Map.of("asignadoAId", instaladorId,
                "fechaHora", OffsetDateTime.now().plusDays(2).toString(), "motivo", "Configurar app del cliente"));
        assertEquals(200, nueva.status(), String.valueOf(nueva.body()));
        assertEquals(3, nueva.body().get("visitas").get(2).get("numero").asInt());
        assertEquals("Configurar app del cliente", nueva.body().get("visitas").get(2).get("motivo").asText());
    }

    private static JsonNode buscar(JsonNode lista, UUID id) {
        for (JsonNode n : lista) {
            if (n.get("id").asText().equals(id.toString())) {
                return n;
            }
        }
        throw new AssertionError("No está en la lista: " + id);
    }
}
