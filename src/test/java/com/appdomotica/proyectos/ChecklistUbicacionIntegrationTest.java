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
 * Checklist por ubicación (informe técnico → proyecto): la copia al iniciar, la regla de completado calculada
 * (todos los puntos marcados + foto de antes y de después), las ubicaciones sin checklist como siempre y el cierre.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChecklistUbicacionIntegrationTest {

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
    private String informeId;

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

    private static final List<String> PUNTOS = List.of("Montar el rack", "Etiquetar los puertos", "Probar los enlaces");

    /** Ubicación 1 (rack) con checklist y una cámara; ubicación 2 (puerta) sin checklist, con una cerradura. */
    private UUID proyecto() throws Exception {
        ProductoCatalogo cam = new ProductoCatalogo();
        cam.setNombre("Cámara checklist " + SECUENCIA.incrementAndGet());
        cam.setCategoria(CategoriaProducto.SEGURIDAD);
        UUID camara = productos.save(cam).getId();
        ProductoCatalogo cer = new ProductoCatalogo();
        cer.setNombre("Cerradura checklist " + SECUENCIA.incrementAndGet());
        cer.setCategoria(CategoriaProducto.CONTROL_ACCESO);
        UUID cerradura = productos.save(cer).getId();
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor checklist " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        UUID proveedor = proveedores.save(prov).getId();

        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Oficina Checklist", "telefono", "8888-4321", "direccion", "Heredia")));
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Cuarto de rack", "items", List.of(Map.of("productoId", camara, "cantidad", 1)),
                                "checklist", PUNTOS),
                        Map.of("nota", "Puerta principal", "items", List.of(Map.of("productoId", cerradura, "cantidad", 1))))));
        assertEquals(201, informe.status(), String.valueOf(informe.body()));
        informeId = informe.body().get("id").asText();
        JsonNode cot = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", informeId)).body();
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

    private Resp marcar(String item, boolean marcado) throws Exception {
        return llamar(put("/api/proyectos/checklist/" + item), instalador, Map.of("marcado", marcado));
    }

    private int fotoUbicacion(UUID proyecto, String ubicacion, String momento) throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "f.jpg", "image/jpeg",
                imagen("jpg", momento.equals("ANTES") ? Color.ORANGE : Color.CYAN));
        return mvc.perform(multipart("/api/proyectos/" + proyecto + "/ubicaciones/" + ubicacion + "/fotos/" + momento)
                .file(f).header("Authorization", instalador)).andReturn().getResponse().getStatus();
    }

    private static JsonNode ubicacion(JsonNode detalle, int numero) {
        return detalle.get("ubicaciones").get(numero - 1);
    }

    // ---------- pruebas ----------

    @Test
    void transferenciaConYSinChecklist() throws Exception {
        UUID p = proyecto();
        JsonNode d = detalle(p);
        JsonNode rack = ubicacion(d, 1);
        JsonNode puerta = ubicacion(d, 2);

        // Con checklist: sus puntos pasan al proyecto en orden y no se crean tareas por producto ahí.
        assertEquals(PUNTOS.size(), rack.get("checklist").size());
        for (int i = 0; i < PUNTOS.size(); i++) {
            assertEquals(PUNTOS.get(i), rack.get("checklist").get(i).get("texto").asText());
            assertFalse(rack.get("checklist").get(i).get("marcado").asBoolean());
        }
        assertEquals("Cuarto de rack", rack.get("nota").asText());
        // Sin checklist: igual que siempre, una tarea por producto. La cámara del rack no queda como tarea general.
        assertEquals(0, puerta.get("checklist").size());
        assertEquals(1, d.get("tareas").size());
        assertEquals(puerta.get("id").asText(), d.get("tareas").get(0).get("ubicacionId").asText());
        assertFalse(rack.get("completada").asBoolean());

        // La barra de avance cuenta tareas más puntos.
        JsonNode resumen = null;
        for (JsonNode r : llamar(get("/api/proyectos"), jefe, null).body()) {
            if (r.get("id").asText().equals(p.toString())) {
                resumen = r;
            }
        }
        assertNotNull(resumen);
        assertEquals(1 + PUNTOS.size(), resumen.get("tareasTotal").asInt());
        assertEquals(0, resumen.get("tareasHechas").asInt());

        // El informe devuelve su checklist.
        JsonNode inf = llamar(get("/api/informes/" + informeId), jefe, null).body();
        assertEquals(PUNTOS, json.convertValue(inf.get("ubicaciones").get(0).get("checklist"), List.class));
        assertEquals(0, inf.get("ubicaciones").get(1).get("checklist").size());
    }

    @Test
    void completadaSoloConTodosLosPuntosYFotos() throws Exception {
        UUID p = proyecto();
        JsonNode rack = ubicacion(detalle(p), 1);
        String ubi = rack.get("id").asText();
        List<String> puntos = new java.util.ArrayList<>();
        rack.get("checklist").forEach(i -> puntos.add(i.get("id").asText()));

        // Todos marcados pero sin fotos: todavía pendiente.
        for (String i : puntos) {
            assertEquals(200, marcar(i, true).status());
        }
        JsonNode d = detalle(p);
        assertFalse(ubicacion(d, 1).get("completada").asBoolean());
        JsonNode item = ubicacion(d, 1).get("checklist").get(0);
        assertTrue(item.get("marcado").asBoolean());
        assertEquals("Prueba INSTALADOR", item.get("marcadoPor").asText());
        assertNotNull(item.get("marcadoEn").asText());
        // Marcar inicia la visita, como completar una tarea; cada punto cuenta en su avance.
        assertEquals(1, d.get("visitas").size());
        assertEquals(PUNTOS.size(), d.get("visitas").get(0).get("tareasHechas").asInt());

        assertEquals(200, fotoUbicacion(p, ubi, "ANTES"));
        assertFalse(ubicacion(detalle(p), 1).get("completada").asBoolean());
        assertEquals(200, fotoUbicacion(p, ubi, "DESPUES"));
        assertTrue(ubicacion(detalle(p), 1).get("completada").asBoolean());

        // Uno desmarcado: vuelve a pendiente; marcarlo otra vez la completa de nuevo.
        Resp desmarcado = marcar(puntos.get(1), false);
        assertEquals(200, desmarcado.status());
        JsonNode suelto = ubicacion(desmarcado.body(), 1).get("checklist").get(1);
        assertFalse(suelto.get("marcado").asBoolean());
        assertTrue(suelto.get("marcadoPor").isNull());
        assertFalse(ubicacion(desmarcado.body(), 1).get("completada").asBoolean());
        assertTrue(ubicacion(marcar(puntos.get(1), true).body(), 1).get("completada").asBoolean());

        // Marcar dos veces lo mismo no cambia nada (gana el último cambio, sin conflicto).
        assertEquals(200, marcar(puntos.get(1), true).status());
        Integer marcadosEnBitacora = jdbc.queryForObject("select count(*) from proyecto_eventos where proyecto_id = ? "
                + "and tipo = 'PUNTO_MARCADO'", Integer.class, p);
        assertEquals(PUNTOS.size() + 1, marcadosEnBitacora);
    }

    @Test
    void sinChecklistSeTrabajaComoSiempreYElCierreExigeLasUbicaciones() throws Exception {
        UUID p = proyecto();
        JsonNode d = detalle(p);
        String rack = ubicacion(d, 1).get("id").asText();
        String puerta = ubicacion(d, 2).get("id").asText();

        // La ubicación sin checklist no acepta fotos de ubicación: van en su tarea.
        assertEquals(409, fotoUbicacion(p, puerta, "ANTES"));
        // Marketing no marca, y sin decir si queda marcado se rechaza.
        String item = ubicacion(d, 1).get("checklist").get(0).get("id").asText();
        assertEquals(403, llamar(put("/api/proyectos/checklist/" + item), marketing, Map.of("marcado", true)).status());
        assertEquals(400, llamar(put("/api/proyectos/checklist/" + item), instalador, Map.of()).status());

        // La tarea de la puerta se completa como hoy.
        JsonNode t = d.get("tareas").get(0);
        foto(t.get("id").asText(), "ANTES");
        foto(t.get("id").asText(), "DESPUES");
        assertEquals(200, estado(t, "COMPLETADA", null, null).status());

        // Falta la ubicación con checklist: no se puede cerrar y el mensaje dice qué falta.
        Resp temprano = cerrar(p, "Ana Cliente", firma(), null);
        assertEquals(409, temprano.status());
        String mensaje = temprano.body().toString();
        assertTrue(mensaje.contains("ubicación 1"), mensaje);
        assertTrue(mensaje.contains("3 puntos sin marcar"), mensaje);

        ubicacion(d, 1).get("checklist").forEach(i -> {
            try {
                assertEquals(200, marcar(i.get("id").asText(), true).status());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(200, fotoUbicacion(p, rack, "ANTES"));
        assertEquals(200, fotoUbicacion(p, rack, "DESPUES"));
        Resp cerrado = cerrar(p, "Ana Cliente", firma(), null);
        assertEquals(200, cerrado.status(), String.valueOf(cerrado.body()));
        assertEquals("CERRADO", cerrado.body().get("estado").asText());
        // Cerrado: ya no se marca ni se desmarca.
        assertEquals(409, marcar(item, false).status());
    }

    @Test
    void laCopiaNoCambiaSiSeEditaElInforme() throws Exception {
        UUID p = proyecto();
        JsonNode antes = ubicacion(detalle(p), 1).get("checklist");

        // Una versión nueva del informe con otra checklist no toca el proyecto ya creado.
        JsonNode v1 = llamar(get("/api/informes/" + informeId), jefe, null).body();
        Map<String, Object> ubi = new java.util.HashMap<>();
        ubi.put("nota", "Cuarto de rack");
        ubi.put("items", List.of(Map.of("productoId", v1.get("ubicaciones").get(0).get("items").get(0).get("productoId").asText(),
                "cantidad", 1)));
        ubi.put("checklist", List.of("Otro punto"));
        Resp v2 = llamar(post("/api/informes/" + informeId + "/versiones"), jefe, Map.of("ubicaciones", List.of(ubi)));
        assertEquals(201, v2.status(), String.valueOf(v2.body()));
        assertEquals(List.of("Otro punto"), json.convertValue(v2.body().get("ubicaciones").get(0).get("checklist"), List.class));

        assertEquals(antes, ubicacion(detalle(p), 1).get("checklist"));
    }

    @Test
    void unPuntoVacioSeRechaza() throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Cliente vacío", "telefono", "8888-1111", "direccion", "Cartago")));
        ProductoCatalogo cam = new ProductoCatalogo();
        cam.setNombre("Cámara vacía " + SECUENCIA.incrementAndGet());
        cam.setCategoria(CategoriaProducto.SEGURIDAD);
        UUID camara = productos.save(cam).getId();
        Resp r = llamar(post("/api/informes"), jefe, Map.of("leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(Map.of("items", List.of(Map.of("productoId", camara, "cantidad", 1)),
                        "checklist", List.of("Bien", "  ")))));
        assertEquals(400, r.status(), String.valueOf(r.body()));
    }
}
