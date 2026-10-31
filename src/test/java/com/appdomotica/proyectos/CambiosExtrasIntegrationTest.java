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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Cambios de instrucciones en sitio y extras de materiales (RF-PRY-03..08, RF-COT-10/11, RN-05, RN-06):
 * proponer, confirmar (versión N+1, checklist y extra), rechazar, y armar, confirmar o descartar el extra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CambiosExtrasIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private String secretaria, jefe, instalador;
    private UUID camara, cerradura, sensor, proveedor, cotizacionId;
    private String ultimoTelefono;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        instalador = token(RolUsuario.INSTALADOR);
    }

    // ---------- utilidades ----------

    private String token(RolUsuario rol) throws Exception {
        ultimoTelefono = "6" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
        Usuario u = new Usuario();
        u.setNombre("Prueba " + rol);
        u.setRol(rol);
        u.setTelefono(ultimoTelefono);
        u.setPasswordHash(encoder.encode(CLAVE));
        u.setActivo(true);
        usuarios.save(u);
        MvcResult r = mvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", ultimoTelefono, "password", CLAVE)))).andReturn();
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

    private UUID producto(String nombre, CategoriaProducto categoria) {
        ProductoCatalogo p = new ProductoCatalogo();
        p.setNombre(nombre + " " + SECUENCIA.incrementAndGet());
        p.setCategoria(categoria);
        return productos.save(p).getId();
    }

    /** Proyecto con una cámara en la ubicación 1 y una cerradura en la 2 (40 000 y 90 000 netos, margen 25 %). */
    private UUID proyecto() throws Exception {
        camara = producto("Cámara cambios", CategoriaProducto.SEGURIDAD);
        cerradura = producto("Cerradura cambios", CategoriaProducto.CONTROL_ACCESO);
        sensor = producto("Sensor cambios", CategoriaProducto.SEGURIDAD);
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor cambios " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        proveedor = proveedores.save(prov).getId();

        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Familia Cambios", "telefono", "8888-1122", "direccion", "Grecia")));
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Cochera", "items", List.of(Map.of("productoId", camara, "cantidad", 1))),
                        Map.of("nota", "Puerta", "items", List.of(Map.of("productoId", cerradura, "cantidad", 1))))));
        assertEquals(201, informe.status(), String.valueOf(informe.body()));
        JsonNode cot = llamar(post("/api/cotizaciones"), secretaria,
                Map.of("informeId", informe.body().get("id").asText())).body();
        String cid = cot.get("id").asText();
        cotizacionId = UUID.fromString(cid);
        cot = llamar(put("/api/cotizaciones/" + cid + "/items"), secretaria, Map.of("version", cot.get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", camara, "proveedorId", proveedor, "cantidad", 1, "precioNeto", 40000),
                        Map.of("productoId", cerradura, "proveedorId", proveedor, "cantidad", 1, "precioNeto", 90000)))).body();
        cot = llamar(put("/api/cotizaciones/" + cid + "/margenes"), jefe,
                Map.of("version", cot.get("version").asLong(), "margenGeneral", 25)).body();
        cot = llamar(post("/api/cotizaciones/" + cid + "/enviar"), secretaria, Map.of("version", cot.get("version").asLong())).body();
        Resp aceptada = llamar(post("/api/cotizaciones/" + cid + "/aceptar"), secretaria,
                Map.of("version", cot.get("version").asLong()));
        Resp iniciada = llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria,
                Map.of("version", aceptada.body().get("version").asLong()));
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        return UUID.fromString(iniciada.body().get("proyectoId").asText());
    }

    private JsonNode detalle(UUID proyecto) throws Exception {
        return llamar(get("/api/proyectos/" + proyecto), instalador, null).body();
    }

    private JsonNode cotizacion() throws Exception {
        return llamar(get("/api/cotizaciones/" + cotizacionId), secretaria, null).body();
    }

    private static JsonNode tareaDe(JsonNode detalle, String textoProducto) {
        for (JsonNode t : detalle.get("tareas")) {
            if (t.get("descripcion").asText().contains(textoProducto)) {
                return t;
            }
        }
        return null;
    }

    private static Map<String, Object> linea(String tipo, Object... pares) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipo", tipo);
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], pares[i + 1]);
        }
        return m;
    }

    /** Tarea automática de la lista de tareas, ligada al proyecto (cambios) o a la cotización (extras). */
    private JsonNode tareaAutomatica(String origen, UUID ligadaA) throws Exception {
        for (JsonNode t : llamar(get("/api/tareas-equipo"), secretaria, null).body()) {
            if (origen.equals(t.get("origen").asText()) && t.hasNonNull("ligada")
                    && t.get("ligada").get("id").asText().equals(ligadaA.toString())) {
                return t;
            }
        }
        return null;
    }

    private static BigDecimal monto(JsonNode n) {
        return new BigDecimal(n.asText()).setScale(2);
    }

    // ---------- pruebas ----------

    @Test
    void unCambioConfirmadoCreaLaVersionNuevaYUnExtraQueSumaAlConfirmarse() throws Exception {
        UUID p = proyecto();
        JsonNode antes = detalle(p);
        JsonNode tareaCamara = tareaDe(antes, "Cámara cambios");
        JsonNode tareaCerradura = tareaDe(antes, "Cerradura cambios");
        String ubicacion1 = antes.get("ubicaciones").get(0).get("id").asText();

        // El instalador propone: otra cámara, quitar la cerradura y 3 sensores en la cochera.
        Resp propuesta = llamar(post("/api/proyectos/" + p + "/cambios"), instalador, Map.of(
                "descripcion", "El cliente quiere vigilar también el patio y cambia la cerradura por su cuenta",
                "requiereMaterial", true,
                "lineas", List.of(
                        linea("CAMBIAR_CANTIDAD", "tareaId", tareaCamara.get("id").asText(), "cantidad", 2),
                        linea("QUITAR", "tareaId", tareaCerradura.get("id").asText()),
                        linea("AGREGAR", "productoId", sensor, "cantidad", 3, "ubicacionId", ubicacion1))));
        assertEquals(200, propuesta.status(), String.valueOf(propuesta.body()));
        JsonNode cambio = propuesta.body().get("cambios").get(0);
        assertEquals("PENDIENTE_CONFIRMACION", cambio.get("estado").asText());
        assertEquals(3, cambio.get("lineas").size());
        assertEquals("Cambiar cantidad de 1 a 2: Instalar 1 × " + tareaCamara.get("productoNombre").asText(),
                cambio.get("lineas").get(0).get("texto").asText());
        assertTrue(cambio.get("lineas").get(2).get("texto").asText().endsWith("(ubicación 1)"));
        // Sin confirmar no toca el checklist, pero ya impide cerrar.
        assertEquals(2, propuesta.body().get("tareas").size());
        assertEquals(1, propuesta.body().get("instrucciones").get("numero").asInt());
        assertTrue(propuesta.body().get("bloqueosCierre").toString().contains("cambio de instrucciones"));
        assertNotNull(tareaAutomatica("CAMBIO_PROPUESTO", p), "aviso en la lista de tareas");

        // Foto opcional del cambio.
        MockMultipartFile foto = new MockMultipartFile("file", "f.jpg", "image/jpeg", imagen());
        assertEquals(200, mvc.perform(multipart("/api/proyectos/cambios/" + cambio.get("id").asText() + "/fotos")
                .file(foto).header("Authorization", instalador)).andReturn().getResponse().getStatus());

        // Solo un jefe confirma (RF-PRY-04).
        Map<String, Object> version = Map.of("version", cambio.get("version").asLong());
        assertEquals(403, llamar(post("/api/proyectos/cambios/" + cambio.get("id").asText() + "/confirmar"),
                instalador, version).status());
        Resp confirmado = llamar(post("/api/proyectos/cambios/" + cambio.get("id").asText() + "/confirmar"), jefe,
                Map.of("version", detalle(p).get("cambios").get(0).get("version").asLong()));
        assertEquals(200, confirmado.status(), String.valueOf(confirmado.body()));
        JsonNode d = confirmado.body();

        // Versión 2 con el motivo y quién propuso (RF-PRY-07).
        assertEquals(2, d.get("instrucciones").get("numero").asInt());
        assertEquals("CAMBIO_SITIO", d.get("instrucciones").get("generadaDesde").asText());
        assertEquals(2, d.get("versiones").size());
        assertTrue(d.get("versiones").get(0).get("motivo").asText().contains("patio"));
        assertEquals("Prueba INSTALADOR", d.get("versiones").get(0).get("propuestoPor").asText());
        JsonNode c = d.get("cambios").get(0);
        assertEquals("CONFIRMADO", c.get("estado").asText());
        assertEquals("Prueba JEFE_SISTEMAS", c.get("resueltoPor").asText());
        assertEquals(1, c.get("fotos").size());
        assertEquals(1, c.get("extraNumero").asInt());

        // Instrucciones v2: cámara modificada, cerradura quitada, sensor agregado.
        Map<String, String> accion = new HashMap<>();
        Map<String, Integer> cantidad = new HashMap<>();
        for (JsonNode i : d.get("instrucciones").get("items")) {
            accion.put(i.get("productoNombre").asText().split(" cambios")[0], i.get("accion").asText());
            cantidad.put(i.get("productoNombre").asText().split(" cambios")[0], i.get("cantidad").asInt());
        }
        assertEquals(Map.of("Cámara", "MODIFICADO", "Cerradura", "QUITADO", "Sensor", "AGREGADO"), accion);
        assertEquals(2, cantidad.get("Cámara"));
        assertEquals(3, cantidad.get("Sensor"));

        // Checklist vigente: cámara x2 (reemplaza a la de x1, que se conserva sin vigencia) y sensores en la cochera.
        assertEquals(2, d.get("tareas").size());
        JsonNode camara2 = tareaDe(d, "Cámara cambios");
        assertEquals(2, camara2.get("cantidad").asInt());
        assertEquals(2, camara2.get("versionInstrucciones").asInt());
        assertEquals(1, camara2.get("ubicacionNumero").asInt());
        assertEquals(1, tareaDe(d, "Sensor cambios").get("ubicacionNumero").asInt());
        assertNull(tareaDe(d, "Cerradura cambios"));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("select vigente from checklist_tareas where id = ?::uuid",
                Boolean.class, tareaCamara.get("id").asText()));
        assertEquals(409, llamar(put("/api/proyectos/tareas/" + tareaCamara.get("id").asText() + "/estado"), instalador,
                Map.of("version", tareaCamara.get("version").asLong(), "estado", "NO_APLICA", "notas", "x")).status());

        // El extra pendiente: material que sube (1 cámara) y lo agregado (3 sensores), sin precios todavía.
        assertEquals(1, d.get("extras").size());
        assertEquals("PENDIENTE", d.get("extras").get(0).get("estado").asText());
        assertEquals(2, d.get("extras").get(0).get("items").size());
        assertTrue(d.get("bloqueosCierre").toString().contains("extra de materiales"));
        assertEquals("HECHA", tareaAutomatica("CAMBIO_PROPUESTO", p).get("estado").asText());
        assertNotNull(tareaAutomatica("EXTRA_PENDIENTE", cotizacionId));

        // Un cambio resuelto no se vuelve a resolver ni se modifica en la base.
        assertEquals(409, llamar(post("/api/proyectos/cambios/" + c.get("id").asText() + "/rechazar"), jefe,
                Map.of("version", c.get("version").asLong(), "motivo", "otra vez")).status());
        assertThrows(Exception.class, () -> jdbc.update(
                "update cambios_instruccion set descripcion = 'x' where id = ?::uuid", c.get("id").asText()));

        // La oficina pone precios; el extra no suma hasta que un jefe lo confirma (RF-COT-11).
        JsonNode cot = cotizacion();
        BigDecimal totalCotizacion = monto(cot.get("totalSinIva"));
        assertEquals(totalCotizacion, monto(cot.get("totalFacturado").get("totalSinIva")));
        JsonNode extra = cot.get("extras").get(0);
        assertEquals("CAMBIO_INSTRUCCION", extra.get("origen").asText());
        String eid = extra.get("id").asText();
        cot = llamar(put("/api/cotizaciones/extras/" + eid + "/items"), secretaria, Map.of(
                "version", extra.get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", camara, "proveedorId", proveedor, "cantidad", 1, "precioNeto", 40000),
                        Map.of("productoId", sensor, "proveedorId", proveedor, "cantidad", 3, "precioNeto", 10000)))).body();
        extra = cot.get("extras").get(0);
        assertEquals(403, llamar(put("/api/cotizaciones/extras/" + eid + "/margenes"), secretaria,
                Map.of("version", extra.get("version").asLong(), "margenGeneral", 25)).status());
        assertEquals(409, llamar(post("/api/cotizaciones/extras/" + eid + "/confirmar"), jefe,
                Map.of("version", extra.get("version").asLong())).status(), "sin margen no se confirma");
        cot = llamar(put("/api/cotizaciones/extras/" + eid + "/margenes"), jefe,
                Map.of("version", extra.get("version").asLong(), "margenGeneral", 25)).body();
        extra = cot.get("extras").get(0);
        assertEquals(new BigDecimal("87500.00"), monto(extra.get("totalSinIva")));
        assertEquals(403, llamar(post("/api/cotizaciones/extras/" + eid + "/confirmar"), secretaria,
                Map.of("version", extra.get("version").asLong())).status());
        Resp confirmadoExtra = llamar(post("/api/cotizaciones/extras/" + eid + "/confirmar"), jefe,
                Map.of("version", extra.get("version").asLong()));
        assertEquals(200, confirmadoExtra.status(), String.valueOf(confirmadoExtra.body()));
        cot = confirmadoExtra.body();
        assertEquals("CONFIRMADO", cot.get("extras").get(0).get("estado").asText());
        assertEquals(totalCotizacion.add(new BigDecimal("87500.00")), monto(cot.get("totalFacturado").get("totalSinIva")));
        assertEquals(monto(cot.get("totalSinIva")), totalCotizacion, "la cotización original no cambia (RN-04)");
        assertEquals("HECHA", tareaAutomatica("EXTRA_PENDIENTE", cotizacionId).get("estado").asText());
        assertFalse(detalle(p).get("bloqueosCierre").toString().contains("extra"));
    }

    @Test
    void unRechazoQuedaRegistradoYNoCreaVersion() throws Exception {
        UUID p = proyecto();
        Resp propuesta = llamar(post("/api/proyectos/" + p + "/cambios"), instalador,
                Map.of("descripcion", "Mover el sensor de la sala a la pared norte", "requiereMaterial", false));
        assertEquals(200, propuesta.status(), String.valueOf(propuesta.body()));
        JsonNode cambio = propuesta.body().get("cambios").get(0);
        // Sin líneas, el cambio deja una tarea con la descripción.
        assertEquals("Agregar tarea: Mover el sensor de la sala a la pared norte",
                cambio.get("lineas").get(0).get("texto").asText());

        String ruta = "/api/proyectos/cambios/" + cambio.get("id").asText() + "/rechazar";
        assertEquals(400, llamar(post(ruta), jefe, Map.of("version", cambio.get("version").asLong(), "motivo", " ")).status());
        Resp rechazado = llamar(post(ruta), jefe,
                Map.of("version", cambio.get("version").asLong(), "motivo", "La pared norte no tiene corriente"));
        assertEquals(200, rechazado.status(), String.valueOf(rechazado.body()));
        JsonNode c = rechazado.body().get("cambios").get(0);
        assertEquals("RECHAZADO", c.get("estado").asText());
        assertEquals("La pared norte no tiene corriente", c.get("motivoRechazo").asText());
        assertEquals(1, rechazado.body().get("instrucciones").get("numero").asInt());
        assertEquals(2, rechazado.body().get("tareas").size());
        assertEquals(0, rechazado.body().get("extras").size());
        assertTrue(rechazado.body().get("bloqueosCierre").isEmpty());
    }

    @Test
    void noSeConfirmaSiLaTareaYaSeCerroDespuesDeProponer() throws Exception {
        UUID p = proyecto();
        JsonNode camaraTarea = tareaDe(detalle(p), "Cámara cambios");
        Resp propuesta = llamar(post("/api/proyectos/" + p + "/cambios"), instalador, Map.of(
                "descripcion", "No hace falta la cámara", "requiereMaterial", false,
                "lineas", List.of(linea("QUITAR", "tareaId", camaraTarea.get("id").asText()))));
        assertEquals(200, propuesta.status(), String.valueOf(propuesta.body()));

        // Mientras tanto, otro instalador la marca "no aplica".
        Resp cerrada = llamar(put("/api/proyectos/tareas/" + camaraTarea.get("id").asText() + "/estado"), instalador,
                Map.of("version", camaraTarea.get("version").asLong(), "estado", "NO_APLICA", "notas", "No hubo cámara"));
        assertEquals(200, cerrada.status(), String.valueOf(cerrada.body()));

        JsonNode cambio = detalle(p).get("cambios").get(0);
        Resp conflicto = llamar(post("/api/proyectos/cambios/" + cambio.get("id").asText() + "/confirmar"), jefe,
                Map.of("version", cambio.get("version").asLong()));
        assertEquals(409, conflicto.status());
        assertTrue(conflicto.body().toString().contains("propón uno nuevo"), String.valueOf(conflicto.body()));
        // Y una tarea ya cerrada no se puede quitar en una propuesta nueva.
        assertEquals(409, llamar(post("/api/proyectos/" + p + "/cambios"), instalador, Map.of(
                "descripcion", "Quitar la cámara", "requiereMaterial", false,
                "lineas", List.of(linea("QUITAR", "tareaId", camaraTarea.get("id").asText())))).status());
    }

    @Test
    void unExtraManualSeDescartaConMotivoYNoSuma() throws Exception {
        UUID p = proyecto();
        Resp creado = llamar(post("/api/cotizaciones/" + cotizacionId + "/extras"), secretaria,
                Map.of("motivo", "Cable adicional para la cochera"));
        assertEquals(201, creado.status(), String.valueOf(creado.body()));
        JsonNode extra = creado.body().get("extras").get(0);
        assertEquals("MANUAL", extra.get("origen").asText());
        assertEquals(1, extra.get("numero").asInt());
        assertTrue(detalle(p).get("bloqueosCierre").toString().contains("extra de materiales"));

        String ruta = "/api/cotizaciones/extras/" + extra.get("id").asText() + "/descartar";
        assertEquals(403, llamar(post(ruta), secretaria,
                Map.of("version", extra.get("version").asLong(), "motivo", "Había en bodega")).status());
        Resp descartado = llamar(post(ruta), jefe, Map.of("version", extra.get("version").asLong(), "motivo", "Había en bodega"));
        assertEquals(200, descartado.status(), String.valueOf(descartado.body()));
        JsonNode cot = descartado.body();
        assertEquals("DESCARTADO", cot.get("extras").get(0).get("estado").asText());
        assertEquals("Había en bodega", cot.get("extras").get(0).get("motivoDescarte").asText());
        assertEquals(monto(cot.get("totalSinIva")), monto(cot.get("totalFacturado").get("totalSinIva")));
        assertTrue(detalle(p).get("bloqueosCierre").isEmpty());
        // Un extra resuelto ya no cambia.
        assertEquals(409, llamar(put("/api/cotizaciones/extras/" + extra.get("id").asText() + "/items"), secretaria,
                Map.of("version", cot.get("extras").get(0).get("version").asLong(), "items",
                        List.of(Map.of("productoId", camara, "cantidad", 1)))).status());
        // El instalador ve el extra en el proyecto, sin precios.
        JsonNode extraProyecto = detalle(p).get("extras").get(0);
        assertEquals("DESCARTADO", extraProyecto.get("estado").asText());
        assertFalse(extraProyecto.toString().contains("precio"));
    }

    private static byte[] imagen() throws Exception {
        BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.PINK);
        g.fillRect(0, 0, 320, 240);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
