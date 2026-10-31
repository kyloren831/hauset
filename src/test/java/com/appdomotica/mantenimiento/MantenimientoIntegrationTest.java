package com.appdomotica.mantenimiento;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.mantenimiento.service.CifradoCredenciales;
import com.appdomotica.mantenimiento.service.RecordatoriosService;
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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Equipos, credenciales cifradas con bitácora y recordatorios a 6 meses (RF-MAN-01..09). */
@SpringBootTest
@AutoConfigureMockMvc
class MantenimientoIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordatoriosService recordatorios;
    @Autowired private CifradoCredenciales cifrado;

    private String secretaria, jefe, instalador, marketing;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        instalador = token(RolUsuario.INSTALADOR);
        marketing = token(RolUsuario.MARKETING);
    }

    // ---------- utilidades ----------

    private String token(RolUsuario rol) throws Exception {
        String telefono = "5" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
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

    private record Resp(int status, JsonNode body, String texto) {
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
        return new Resp(r.getResponse().getStatus(), body, texto);
    }

    private String cliente;

    /** Proyecto iniciado con una cámara y una cerradura en el checklist. */
    private UUID proyecto() throws Exception {
        ProductoCatalogo cam = new ProductoCatalogo();
        cam.setNombre("Cámara mant " + SECUENCIA.incrementAndGet());
        cam.setCategoria(CategoriaProducto.SEGURIDAD);
        UUID camara = productos.save(cam).getId();
        ProductoCatalogo cer = new ProductoCatalogo();
        cer.setNombre("Cerradura mant " + SECUENCIA.incrementAndGet());
        cer.setCategoria(CategoriaProducto.CONTROL_ACCESO);
        UUID cerradura = productos.save(cer).getId();
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor mant " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        UUID proveedor = proveedores.save(prov).getId();

        cliente = "Familia Mantenimiento " + SECUENCIA.incrementAndGet();
        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", cliente, "telefono", "8888-4321", "direccion", "Palmares")));
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Portón", "items", List.of(Map.of("productoId", camara, "cantidad", 1))),
                        Map.of("nota", "Puerta", "items", List.of(Map.of("productoId", cerradura, "cantidad", 1))))));
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
        Resp iniciada = llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria,
                Map.of("version", aceptada.body().get("version").asLong()));
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        return UUID.fromString(iniciada.body().get("proyectoId").asText());
    }

    /** Todas las tareas "no aplica" y firma del cliente. */
    private void cerrar(UUID proyecto) throws Exception {
        for (JsonNode t : llamar(get("/api/proyectos/" + proyecto), jefe, null).body().get("tareas")) {
            Resp r = llamar(put("/api/proyectos/tareas/" + t.get("id").asText() + "/estado"), instalador,
                    Map.of("version", t.get("version").asLong(), "estado", "NO_APLICA", "notas", "Prueba"));
            assertEquals(200, r.status(), String.valueOf(r.body()));
        }
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLACK);
        g.drawLine(10, 80, 190, 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        int status = mvc.perform(multipart("/api/proyectos/" + proyecto + "/cierre")
                        .file(new MockMultipartFile("firma", "firma.png", "image/png", out.toByteArray()))
                        .param("nombre", "Ana Cliente").header("Authorization", instalador))
                .andReturn().getResponse().getStatus();
        assertEquals(200, status);
    }

    private Resp registrar(UUID proyecto, String bearer, Map<String, Object> cuerpo) throws Exception {
        return llamar(post("/api/proyectos/" + proyecto + "/equipos"), bearer, cuerpo);
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String mac() {
        // Al azar: la base de pruebas guarda lo de corridas anteriores.
        String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return String.join("-", hex.split("(?<=\\G..)"));
    }

    // ---------- pruebas ----------

    @Test
    void equiposYCredencialesCifradasConBitacora() throws Exception {
        UUID p = proyecto();
        String suCliente = cliente;
        JsonNode vacio = llamar(get("/api/proyectos/" + p + "/equipos"), instalador, null).body();
        assertEquals(0, vacio.get("equipos").size());
        assertEquals(2, vacio.get("productos").size(), "ofrece los productos del checklist");
        String productoId = vacio.get("productos").get(0).get("id").asText();

        // Registrar: la MAC se normaliza y la fecha de instalación espera al cierre.
        String laMac = mac();
        Resp alta = registrar(p, instalador, mapa("nombreReferencia", "Cámara del portón", "productoId", productoId,
                "numeroSerie", "SN-001", "direccionMac", laMac));
        assertEquals(201, alta.status(), String.valueOf(alta.body()));
        JsonNode equipo = alta.body().get("equipos").get(0);
        assertEquals(laMac.toUpperCase().replace('-', ':'), equipo.get("direccionMac").asText());
        assertTrue(equipo.get("fechaInstalacion").isNull());

        // Equipo del cliente, sin producto del catálogo.
        assertEquals(201, registrar(p, jefe, mapa("nombreReferencia", "Router del cliente")).status());

        // MAC y serie repetidas (la serie, por producto), MAC mal escrita, roles sin permiso.
        UUID otro = proyecto();
        assertEquals(409, registrar(otro, instalador, mapa("nombreReferencia", "X", "direccionMac", laMac)).status());
        assertEquals(409, registrar(p, instalador, mapa("nombreReferencia", "X", "productoId", productoId,
                "numeroSerie", "sn-001")).status());
        assertEquals(400, registrar(p, instalador, mapa("nombreReferencia", "X", "direccionMac", "12:34")).status());
        assertEquals(400, registrar(p, instalador, mapa("nombreReferencia", " ")).status());
        assertEquals(403, registrar(p, secretaria, mapa("nombreReferencia", "X")).status());
        assertEquals(403, llamar(get("/api/proyectos/" + p + "/equipos"), marketing, null).status());

        // Credencial: la contraseña se guarda cifrada y nunca sale en las listas.
        String equipoId = equipo.get("id").asText();
        Resp cred = llamar(post("/api/equipos/" + equipoId + "/credenciales"), instalador,
                mapa("tipo", "WIFI", "usuario", "CasaMora", "contrasena", "S3creta-Wifi!", "notas", "Red de invitados"));
        assertEquals(201, cred.status(), String.valueOf(cred.body()));
        assertFalse(cred.texto().contains("S3creta-Wifi!"));
        assertEquals(400, llamar(post("/api/equipos/" + equipoId + "/credenciales"), instalador,
                mapa("tipo", "WIFI", "contrasena", "")).status());
        JsonNode c = cred.body().get("equipos").get(0).get("credenciales").get(0);
        String credId = c.get("id").asText();
        assertEquals(0, c.get("consultas").asLong());
        String guardada = jdbc.queryForObject("select contrasena_cifrada from credenciales_equipo where id = ?::uuid",
                String.class, credId);
        assertTrue(guardada.startsWith("v1:"));
        assertFalse(guardada.contains("S3creta"));
        assertFalse(llamar(get("/api/equipos?buscar=" + suCliente.substring(8)), instalador, null).texto().contains("S3creta"));

        // Verla (también la secretaria) queda en la bitácora; solo los jefes leen la bitácora.
        Resp vista = llamar(post("/api/credenciales/" + credId + "/revelar"), secretaria, null);
        assertEquals(200, vista.status());
        assertEquals("S3creta-Wifi!", vista.body().get("contrasena").asText());
        assertEquals(403, llamar(post("/api/credenciales/" + credId + "/revelar"), marketing, null).status());
        JsonNode accesos = llamar(get("/api/credenciales/" + credId + "/accesos"), jefe, null).body();
        assertEquals(1, accesos.size());
        assertEquals("Prueba SECRETARIA", accesos.get(0).get("persona").asText());
        assertEquals(403, llamar(get("/api/credenciales/" + credId + "/accesos"), instalador, null).status());
        assertEquals(1, llamar(get("/api/proyectos/" + p + "/equipos"), jefe, null).body()
                .get("equipos").get(0).get("credenciales").get(0).get("consultas").asLong());

        // La bitácora no se edita ni se borra (RF-MAN-06).
        assertThrows(Exception.class, () -> jdbc.update(
                "update credenciales_accesos_log set fecha = now() where credencial_id = ?::uuid", credId));
        assertThrows(Exception.class, () -> jdbc.update(
                "delete from credenciales_accesos_log where credencial_id = ?::uuid", credId));

        // Editar sin contraseña conserva la anterior; con versión vieja, conflicto.
        Resp editada = llamar(put("/api/credenciales/" + credId), instalador,
                mapa("tipo", "WIFI", "usuario", "CasaMora-5G", "version", c.get("version").asLong()));
        assertEquals(200, editada.status(), String.valueOf(editada.body()));
        assertEquals("S3creta-Wifi!", llamar(post("/api/credenciales/" + credId + "/revelar"), jefe, null)
                .body().get("contrasena").asText());
        // Con clave de idempotencia (la app la pone a todo POST): no se guarda la respuesta ni se repite sin registro.
        String clave = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            MvcResult r = mvc.perform(post("/api/credenciales/" + credId + "/revelar")
                    .header("Authorization", instalador).header("Idempotency-Key", clave)).andReturn();
            assertEquals(200, r.getResponse().getStatus());
            assertNull(r.getResponse().getHeader("Idempotent-Replayed"));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from solicitudes_idempotentes where clave = ?",
                Integer.class, clave));
        assertEquals(4, jdbc.queryForObject("select count(*) from credenciales_accesos_log where credencial_id = ?::uuid",
                Integer.class, credId));
        assertEquals(409, llamar(put("/api/credenciales/" + credId), instalador,
                mapa("tipo", "WIFI", "version", c.get("version").asLong())).status());

        // Buscar por cliente o MAC.
        JsonNode encontrados = llamar(get("/api/equipos?buscar=" + laMac.substring(9)), secretaria, null).body();
        assertEquals(1, encontrados.size());
        assertEquals(suCliente, encontrados.get(0).get("clienteNombre").asText());
        assertEquals(1, encontrados.get(0).get("credenciales").asInt());

        // Dar de baja: no se borra (la bitácora la referencia), pero ya no se lista ni se ve.
        long version = editada.body().get("equipos").get(0).get("credenciales").get(0).get("version").asLong();
        Resp baja = llamar(delete("/api/credenciales/" + credId + "?version=" + version), instalador, null);
        assertEquals(200, baja.status(), String.valueOf(baja.body()));
        assertEquals(0, baja.body().get("equipos").get(0).get("credenciales").size());
        assertEquals(404, llamar(post("/api/credenciales/" + credId + "/revelar"), jefe, null).status());
        assertEquals(1, jdbc.queryForObject("select count(*) from credenciales_equipo where id = ?::uuid",
                Integer.class, credId));

        // Retirar el equipo libera su MAC.
        JsonNode e = baja.body().get("equipos").get(0);
        assertEquals(200, llamar(delete("/api/equipos/" + e.get("id").asText() + "?version=" + e.get("version").asLong()),
                jefe, null).status());
        assertEquals(201, registrar(otro, instalador, mapa("nombreReferencia", "Cámara reubicada", "direccionMac", laMac)).status());
    }

    @Test
    void elCifradoAtaLaContrasenaASuCredencial() {
        UUID a = UUID.randomUUID();
        String guardada = cifrado.cifrar(a, "clave");
        assertNotEquals(guardada, cifrado.cifrar(a, "clave"), "nonce distinto cada vez");
        assertEquals("clave", cifrado.descifrar(a, guardada));
        assertThrows(IllegalStateException.class, () -> cifrado.descifrar(UUID.randomUUID(), guardada));
        assertThrows(IllegalStateException.class, () -> new CifradoCredenciales("corta"));
        // Ni la petición ni la respuesta muestran la contraseña si se imprimen en el log.
        assertFalse(new com.appdomotica.mantenimiento.dto.MantenimientoDtos.CredencialRequest(
                com.appdomotica.mantenimiento.entity.TipoCredencial.WIFI, "u", "clave", null, null).toString().contains("clave"));
        assertFalse(new com.appdomotica.mantenimiento.dto.MantenimientoDtos.CredencialRevelada(a, "clave")
                .toString().contains("clave"));
    }

    @Test
    void alCerrarSeFijaLaInstalacionYAlosSeisMesesHayRecordatorio() throws Exception {
        UUID p = proyecto();
        assertEquals(201, registrar(p, instalador, mapa("nombreReferencia", "Cámara del portón")).status());
        cerrar(p);
        LocalDate hoy = LocalDate.now(RecordatoriosService.CR);

        JsonNode equipos = llamar(get("/api/proyectos/" + p + "/equipos"), instalador, null).body();
        assertEquals(hoy.toString(), equipos.get("fechaCierre").asText());
        assertEquals(hoy.toString(), equipos.get("equipos").get(0).get("fechaInstalacion").asText());
        // Uno que se registra después del cierre toma la fecha del cierre.
        JsonNode tarde = registrar(p, instalador, mapa("nombreReferencia", "Router")).body();
        assertEquals(hoy.toString(), tarde.get("equipos").get(1).get("fechaInstalacion").asText());

        LocalDate aSeisMeses = hoy.plusMonths(6);
        // Antes de tiempo no se crea, pero aparece entre los próximos.
        recordatorios.generar(aSeisMeses.minusDays(10));
        assertEquals(0, cuantos(p));
        assertTrue(recordatorios.listar(aSeisMeses.minusDays(10)).proximos().stream()
                .anyMatch(x -> x.proyectoId().equals(p) && x.fechaProgramada().equals(aSeisMeses)));

        // El día que toca (o después, si el servidor estuvo apagado), uno solo aunque corra varias veces.
        recordatorios.generar(aSeisMeses.plusDays(3));
        recordatorios.generar(aSeisMeses.plusDays(4));
        assertEquals(1, cuantos(p));

        Resp lista = llamar(get("/api/mantenimiento/recordatorios"), secretaria, null);
        assertEquals(200, lista.status());
        JsonNode r = null;
        for (JsonNode x : lista.body().get("pendientes")) {
            if (x.get("proyectoId").asText().equals(p.toString())) {
                r = x;
            }
        }
        assertNotNull(r);
        assertEquals(aSeisMeses.toString(), r.get("fechaProgramada").asText());
        assertEquals("88884321", r.get("clienteTelefono").asText());
        String mensaje = r.get("mensaje").asText();
        assertTrue(mensaje.startsWith("Hola " + cliente), mensaje);
        assertTrue(mensaje.contains("Cámara del portón y Router"), mensaje);
        assertTrue(mensaje.contains("mantenimiento preventivo"), mensaje);

        // Lo ven y lo resuelven jefes, ingeniero industrial y secretaria; el instalador no.
        assertEquals(403, llamar(get("/api/mantenimiento/recordatorios"), instalador, null).status());
        String id = r.get("id").asText();
        Resp hecho = llamar(post("/api/mantenimiento/recordatorios/" + id + "/contactado"), secretaria,
                mapa("version", r.get("version").asLong(), "nota", "Agenda para la otra semana"));
        assertEquals(200, hecho.status(), String.valueOf(hecho.body()));
        assertEquals("CONTACTADO", hecho.body().get("estado").asText());
        assertEquals("Prueba SECRETARIA", hecho.body().get("resueltoPor").asText());
        assertEquals(409, llamar(post("/api/mantenimiento/recordatorios/" + id + "/omitido"), jefe,
                mapa("version", hecho.body().get("version").asLong())).status());
        JsonNode despues = llamar(get("/api/mantenimiento/recordatorios"), jefe, null).body();
        assertTrue(despues.get("recientes").findValuesAsText("id").contains(id));
        assertFalse(despues.get("pendientes").findValuesAsText("id").contains(id));
    }

    private int cuantos(UUID proyecto) {
        return jdbc.queryForObject("select count(*) from mantenimiento_recordatorios where proyecto_id = ?",
                Integer.class, proyecto);
    }
}
