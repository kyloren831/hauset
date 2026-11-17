package com.appdomotica.cotizaciones;

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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Una cotización aceptada no se recotiza (RF-COT-21, RF-COT-22): se desactiva (solo sin proyecto) para
 * recotizar, o una versión nueva del informe la actualiza junto con su proyecto y el material de más queda
 * como extra pendiente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CotizacionAceptadaIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000) + 500_000);
    private static final String CLAVE = "clave-de-prueba-123";
    private static final List<String> PUNTOS = List.of("Montar el rack", "Etiquetar los puertos", "Probar los enlaces");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private String secretaria, jefe, jefeElectro, instalador;
    private UUID camara, cerradura, proveedor;
    private String leadId;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        jefeElectro = token(RolUsuario.JEFE_ELECTROMECANICO);
        instalador = token(RolUsuario.INSTALADOR);
        camara = producto("Cámara aceptada", CategoriaProducto.SEGURIDAD);
        cerradura = producto("Cerradura aceptada", CategoriaProducto.CONTROL_ACCESO);
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor aceptada " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        proveedor = proveedores.save(prov).getId();
        leadId = llamar(post("/api/leads"), secretaria, Map.of("cliente",
                Map.of("nombre", "Cliente aceptada", "telefono", "8888-1111", "direccion", "Cartago"))).body()
                .get("id").asText();
    }

    // ---------- 1. Sin proyecto: no se recotiza; la versión nueva actualiza la cotización ----------

    @Test
    void versionNuevaDeUnaAceptadaSinProyectoLaActualizaYNoSeRecotiza() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));

        JsonNode v2 = version(v1, List.of(ubicacion(id(v1, 0), "Sala", Map.of(camara, 3), null)));
        assertEquals(201, v2.get("_status").asInt(), v2.toString());
        // La versión nueva pasa a ser la de la cotización aceptada.
        assertEquals(cid, v2.get("cotizacion").get("cotizacionId").asText());
        assertEquals(cid, v2.get("aceptada").get("cotizacionId").asText());
        JsonNode cot = llamar(get("/api/cotizaciones/" + cid), secretaria, null).body();
        assertEquals(2, cot.get("informeVersion").asInt());
        assertEquals("ACEPTADA", cot.get("estado").asText());
        // Lo que la v.2 pide de más queda como extra pendiente.
        assertEquals(1, cot.get("extras").size());
        JsonNode extra = cot.get("extras").get(0);
        assertEquals("INFORME_TECNICO", extra.get("origen").asText());
        assertEquals("PENDIENTE", extra.get("estado").asText());
        assertEquals(2, extra.get("items").get(0).get("cantidad").asInt());

        // Ni la v.1 ni la v.2 se cotizan aparte.
        Resp otraV2 = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", v2.get("id").asText()));
        assertEquals(409, otraV2.status());
        Resp otraV1 = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", v1.get("id").asText()));
        assertEquals(409, otraV1.status());
        assertTrue(otraV1.body().get("message").asText().contains("ya tiene la cotización aceptada"),
                otraV1.body().toString());

        // La v.1 queda como historial: muestra la cotización y ya no admite cambios de fotos.
        JsonNode v1Ahora = llamar(get("/api/informes/" + v1.get("id").asText()), jefe, null).body();
        assertEquals(cid, v1Ahora.get("cotizacion").get("cotizacionId").asText());
        assertFalse(v1Ahora.get("fotosEditables").asBoolean());
        Resp borrarFoto = llamar(delete("/api/informes/" + v1.get("id").asText() + "/ubicaciones/" + id(v1, 0) + "/foto"),
                jefe, null);
        assertEquals(409, borrarFoto.status());
    }

    @Test
    void noSeAceptanDosCotizacionesDelMismoInforme() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String a = enviar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));
        JsonNode v2 = version(v1, List.of(ubicacion(id(v1, 0), "Sala", Map.of(camara, 2), null)));
        String b = enviar(cotizar(v2.get("id").asText(), Map.of(camara, 2)));
        aceptar(b);
        Resp otra = llamar(post("/api/cotizaciones/" + a + "/aceptar"), secretaria, Map.of("version", version(a)));
        assertEquals(409, otra.status());
        assertTrue(otra.body().get("message").asText().contains(numero(b)), otra.body().toString());
    }

    // ---------- 2. Desactivar para recotizar ----------

    @Test
    void desactivarUnaAceptadaSinProyectoPermiteRecotizar() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));

        assertEquals(403, llamar(post("/api/cotizaciones/" + cid + "/desactivar"), instalador,
                Map.of("version", version(cid), "motivo", "x")).status());
        assertEquals(400, llamar(post("/api/cotizaciones/" + cid + "/desactivar"), secretaria,
                Map.of("version", version(cid), "motivo", " ")).status());
        Resp desactivada = llamar(post("/api/cotizaciones/" + cid + "/desactivar"), secretaria,
                Map.of("version", version(cid), "motivo", "El cliente cambió todo el alcance"));
        assertEquals(200, desactivada.status(), String.valueOf(desactivada.body()));
        assertEquals("DESACTIVADA", desactivada.body().get("estado").asText());
        assertEquals("El cliente cambió todo el alcance", desactivada.body().get("motivoDesactivacion").asText());
        assertNotNull(desactivada.body().get("desactivadaPor").asText());

        // Ya no aparece para iniciar ni se puede iniciar; la tarea automática se cerró.
        JsonNode porIniciar = llamar(get("/api/cotizaciones/por-iniciar"), jefe, null).body();
        assertTrue(StreamSupport.stream(porIniciar.spliterator(), false).noneMatch(c -> c.get("id").asText().equals(cid)));
        assertEquals(409, llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria,
                Map.of("version", version(cid))).status());
        assertEquals(0, jdbc.queryForObject("select count(*) from tareas_equipo where cotizacion_id = ?::uuid "
                + "and origen = 'COTIZACION_ACEPTADA' and estado <> 'HECHA'", Integer.class, cid));
        // Desactivar otra vez no se puede.
        assertEquals(409, llamar(post("/api/cotizaciones/" + cid + "/desactivar"), jefe,
                Map.of("version", version(cid), "motivo", "otra vez")).status());

        // La versión nueva ya no queda ligada a la desactivada y se cotiza de nuevo.
        JsonNode v2 = version(v1, List.of(ubicacion(id(v1, 0), "Sala", Map.of(camara, 2), null)));
        assertTrue(v2.get("cotizacion").isNull(), v2.toString());
        assertTrue(v2.get("aceptada").isNull());
        assertEquals(201, llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", v2.get("id").asText())).status());
    }

    /** Un extra pendiente impide desactivar; uno confirmado se queda en la desactivada y no pasa a la nueva. */
    @Test
    void desactivarConExtras() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));
        JsonNode extra = llamar(post("/api/cotizaciones/" + cid + "/extras"), secretaria,
                Map.of("motivo", "Una cerradura más")).body().get("extras").get(0);
        String eid = extra.get("id").asText();
        Resp pendiente = llamar(post("/api/cotizaciones/" + cid + "/desactivar"), secretaria,
                Map.of("version", version(cid), "motivo", "Recotizar"));
        assertEquals(409, pendiente.status());
        assertTrue(pendiente.body().get("message").asText().contains("extras pendientes"), pendiente.body().toString());

        extra = llamar(put("/api/cotizaciones/extras/" + eid + "/items"), secretaria, Map.of("version",
                extra.get("version").asLong(), "items", List.of(Map.of("productoId", cerradura, "proveedorId", proveedor,
                        "cantidad", 1, "precioNeto", 40000)))).body().get("extras").get(0);
        extra = llamar(put("/api/cotizaciones/extras/" + eid + "/margenes"), jefe,
                Map.of("version", extra.get("version").asLong(), "margenGeneral", 25)).body().get("extras").get(0);
        assertEquals(200, llamar(post("/api/cotizaciones/extras/" + eid + "/confirmar"), jefe,
                Map.of("version", extra.get("version").asLong())).status());

        Resp desactivada = llamar(post("/api/cotizaciones/" + cid + "/desactivar"), secretaria,
                Map.of("version", version(cid), "motivo", "Recotizar"));
        assertEquals(200, desactivada.status(), String.valueOf(desactivada.body()));
        assertEquals("CONFIRMADO", desactivada.body().get("extras").get(0).get("estado").asText());
        // A una desactivada no se le agregan extras, y la cotización nueva empieza sin ellos.
        assertEquals(409, llamar(post("/api/cotizaciones/" + cid + "/extras"), secretaria, Map.of("motivo", "x")).status());
        JsonNode v2 = version(v1, List.of(ubicacion(id(v1, 0), "Sala", Map.of(camara, 1, cerradura, 1), null)));
        String nueva = cotizar(v2.get("id").asText(), Map.of(camara, 1, cerradura, 1));
        assertEquals(0, llamar(get("/api/cotizaciones/" + nueva), secretaria, null).body().get("extras").size());
    }

    @Test
    void conProyectoNoSeDesactiva() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));
        iniciar(cid);
        Resp r = llamar(post("/api/cotizaciones/" + cid + "/desactivar"), jefeElectro,
                Map.of("version", version(cid), "motivo", "Cambio de alcance"));
        assertEquals(409, r.status());
        assertTrue(r.body().get("message").asText().contains("versión nueva del informe"), r.body().toString());
    }

    // ---------- 3. Con proyecto: la versión nueva actualiza el proyecto ----------

    @Test
    void versionNuevaActualizaElProyectoSinPerderLoHecho() throws Exception {
        // v.1: ubicación 1 con checklist y una cámara; ubicación 2 con una cerradura.
        JsonNode v1 = informe(List.of(
                ubicacion(null, "Rack", Map.of(camara, 1), PUNTOS),
                ubicacion(null, "Puerta", Map.of(cerradura, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1, cerradura, 1)));
        UUID proyecto = iniciar(cid);

        JsonNode antes = detalle(proyecto);
        JsonNode rack = antes.get("ubicaciones").get(0);
        String montar = punto(rack, "Montar el rack").get("id").asText();
        assertEquals(200, llamar(put("/api/proyectos/checklist/" + montar), instalador, Map.of("marcado", true)).status());
        JsonNode tareaCerradura = antes.get("tareas").get(0);
        long versionTareaCerradura = tareaCerradura.get("version").asLong();

        // v.2: la checklist cambia (se quita un punto, se agrega otro), la puerta pide 2 cerraduras y hay una
        // ubicación nueva con una cámara.
        JsonNode v2 = version(v1, List.of(
                ubicacion(id(v1, 0), "Rack", Map.of(camara, 1), List.of("Montar el rack", "Probar los enlaces", "Ordenar cables")),
                ubicacion(id(v1, 1), "Puerta", Map.of(cerradura, 2), null),
                ubicacion(null, "Cochera", Map.of(camara, 1), null)));
        assertEquals(201, v2.get("_status").asInt(), v2.toString());

        JsonNode d = detalle(proyecto);
        assertEquals(2, d.get("informeVersion").asInt());
        assertEquals(2, d.get("instrucciones").get("numero").asInt(), d.get("instrucciones").toString());
        assertEquals("INFORME_TECNICO", d.get("instrucciones").get("generadaDesde").asText());
        assertEquals(3, d.get("ubicaciones").size());

        // Checklist: lo marcado se conserva; el orden es el de la v.2.
        JsonNode rack2 = d.get("ubicaciones").get(0);
        assertEquals(List.of("Montar el rack", "Probar los enlaces", "Ordenar cables"), textos(rack2.get("checklist")));
        assertTrue(punto(rack2, "Montar el rack").get("marcado").asBoolean());
        assertEquals(montar, punto(rack2, "Montar el rack").get("id").asText());

        // Puerta: la tarea original sigue igual (misma versión, no choca con un instalador sin conexión) y se
        // agrega una por la cerradura de más.
        UUID puerta = UUID.fromString(d.get("ubicaciones").get(1).get("id").asText());
        List<JsonNode> enPuerta = tareasEn(d, puerta);
        assertEquals(2, enPuerta.size(), enPuerta.toString());
        JsonNode original = enPuerta.stream().filter(t -> t.get("id").asText().equals(tareaCerradura.get("id").asText()))
                .findFirst().orElseThrow();
        assertEquals(versionTareaCerradura, original.get("version").asLong());
        assertEquals(2, original.get("ubicacionNumero").asInt());
        UUID cochera = UUID.fromString(d.get("ubicaciones").get(2).get("id").asText());
        assertEquals(1, tareasEn(d, cochera).size());

        // El material de más (una cerradura y una cámara) queda como extra pendiente.
        JsonNode cot = llamar(get("/api/cotizaciones/" + cid), secretaria, null).body();
        assertEquals(1, cot.get("extras").size());
        assertEquals(2, cot.get("extras").get(0).get("items").size());
        assertTrue(StreamSupport.stream(d.get("eventos").spliterator(), false)
                .anyMatch(e -> e.get("tipo").asText().equals("INFORME_ACTUALIZADO")));

        // La tarea original se cierra y llega la v.3: la puerta vuelve a 1 cerradura y la cochera se quita.
        Resp cerrada = llamar(put("/api/proyectos/tareas/" + original.get("id").asText() + "/estado"), instalador,
                Map.of("version", original.get("version").asLong(), "estado", "NO_APLICA",
                        "notas", "El cliente ya la tenía instalada"));
        assertEquals(200, cerrada.status(), String.valueOf(cerrada.body()));
        JsonNode v3 = version(v2, List.of(
                ubicacion(id(v2, 0), "Rack", Map.of(camara, 1), List.of("Montar el rack", "Probar los enlaces", "Ordenar cables")),
                ubicacion(id(v2, 1), "Puerta", Map.of(cerradura, 1), null)));
        assertEquals(201, v3.get("_status").asInt(), v3.toString());
        JsonNode d3 = detalle(proyecto);
        assertEquals(3, d3.get("informeVersion").asInt());
        assertEquals(2, d3.get("ubicaciones").size());
        List<JsonNode> enPuerta3 = tareasEn(d3, UUID.fromString(d3.get("ubicaciones").get(1).get("id").asText()));
        assertEquals(1, enPuerta3.size(), enPuerta3.toString());
        assertEquals("NO_APLICA", enPuerta3.get(0).get("estado").asText());
        // Solo quedan las tareas vigentes de las ubicaciones que siguen (la de la cochera se quitó).
        assertEquals(1, d3.get("tareas").size(), d3.get("tareas").toString());
        // Sin material de más, no hay extra nuevo.
        assertEquals(1, llamar(get("/api/cotizaciones/" + cid), secretaria, null).body().get("extras").size());
    }

    @Test
    void conProyectoCerradoNoSeCreaVersion() throws Exception {
        JsonNode v1 = informe(List.of(ubicacion(null, "Sala", Map.of(camara, 1), null)));
        String cid = aceptar(cotizar(v1.get("id").asText(), Map.of(camara, 1)));
        UUID proyecto = iniciar(cid);
        jdbc.update("update proyectos set estado = 'CERRADO' where id = ?", proyecto);
        JsonNode v2 = version(v1, List.of(ubicacion(id(v1, 0), "Sala", Map.of(camara, 2), null)));
        assertEquals(409, v2.get("_status").asInt());
        assertTrue(v2.get("message").asText().contains("informe técnico nuevo"), v2.toString());
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
        usuarios.save(u);
        MvcResult r = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", telefono, "password", CLAVE)))).andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private UUID producto(String nombre, CategoriaProducto categoria) {
        ProductoCatalogo p = new ProductoCatalogo();
        p.setNombre(nombre + " " + SECUENCIA.incrementAndGet());
        p.setCategoria(categoria);
        return productos.save(p).getId();
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

    private static Map<String, Object> ubicacion(String desde, String nota, Map<UUID, Integer> items, List<String> checklist) {
        Map<String, Object> u = new LinkedHashMap<>();
        if (desde != null) {
            u.put("desde", desde);
        }
        u.put("nota", nota);
        u.put("items", items.entrySet().stream()
                .map(e -> Map.of("productoId", e.getKey(), "cantidad", e.getValue())).toList());
        if (checklist != null) {
            u.put("checklist", checklist);
        }
        return u;
    }

    private JsonNode informe(List<Map<String, Object>> ubicaciones) throws Exception {
        Resp r = llamar(post("/api/informes"), jefe, Map.of("leadId", leadId, "ubicaciones", ubicaciones));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    /** Versión nueva; devuelve el cuerpo con el código HTTP en `_status`. */
    private JsonNode version(JsonNode desde, List<Map<String, Object>> ubicaciones) throws Exception {
        Resp r = llamar(post("/api/informes/" + desde.get("id").asText() + "/versiones"), jefe,
                Map.of("ubicaciones", ubicaciones));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) r.body();
        body.put("_status", r.status());
        return body;
    }

    private static String id(JsonNode informe, int ubicacion) {
        return informe.get("ubicaciones").get(ubicacion).get("id").asText();
    }

    /** Cotización desde el informe con precios y margen, lista para enviar. */
    private String cotizar(String informeId, Map<UUID, Integer> cantidades) throws Exception {
        JsonNode cot = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", informeId)).body();
        String cid = cot.get("id").asText();
        cot = llamar(put("/api/cotizaciones/" + cid + "/items"), secretaria, Map.of("version", cot.get("version").asLong(),
                "items", cantidades.entrySet().stream().map(e -> Map.of("productoId", e.getKey(), "proveedorId", proveedor,
                        "cantidad", e.getValue(), "precioNeto", 50000)).toList())).body();
        llamar(put("/api/cotizaciones/" + cid + "/margenes"), jefe,
                Map.of("version", cot.get("version").asLong(), "margenGeneral", 20));
        return cid;
    }

    private String enviar(String cid) throws Exception {
        Resp r = llamar(post("/api/cotizaciones/" + cid + "/enviar"), secretaria, Map.of("version", version(cid)));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return cid;
    }

    private String aceptar(String cid) throws Exception {
        if (!"ENVIADA".equals(llamar(get("/api/cotizaciones/" + cid), secretaria, null).body().get("estado").asText())) {
            enviar(cid);
        }
        Resp r = llamar(post("/api/cotizaciones/" + cid + "/aceptar"), secretaria, Map.of("version", version(cid)));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return cid;
    }

    private UUID iniciar(String cid) throws Exception {
        Resp r = llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria, Map.of("version", version(cid)));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return UUID.fromString(r.body().get("proyectoId").asText());
    }

    private long version(String cid) throws Exception {
        return llamar(get("/api/cotizaciones/" + cid), secretaria, null).body().get("version").asLong();
    }

    private String numero(String cid) throws Exception {
        return llamar(get("/api/cotizaciones/" + cid), secretaria, null).body().get("numero").asText();
    }

    private JsonNode detalle(UUID proyecto) throws Exception {
        return llamar(get("/api/proyectos/" + proyecto), jefe, null).body();
    }

    private static JsonNode punto(JsonNode ubicacion, String texto) {
        return StreamSupport.stream(ubicacion.get("checklist").spliterator(), false)
                .filter(p -> p.get("texto").asText().equals(texto)).findFirst().orElseThrow();
    }

    private static List<String> textos(JsonNode checklist) {
        return StreamSupport.stream(checklist.spliterator(), false).map(p -> p.get("texto").asText()).toList();
    }

    private static List<JsonNode> tareasEn(JsonNode detalle, UUID ubicacion) {
        return StreamSupport.stream(detalle.get("tareas").spliterator(), false)
                .filter(t -> ubicacion.toString().equals(t.get("ubicacionId").asText())).toList();
    }
}
