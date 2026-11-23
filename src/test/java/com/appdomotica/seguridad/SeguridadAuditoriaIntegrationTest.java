package com.appdomotica.seguridad;

import com.appdomotica.catalogo.entity.CategoriaProducto;
import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.catalogo.entity.Proveedor;
import com.appdomotica.catalogo.repository.ProductoCatalogoRepository;
import com.appdomotica.catalogo.repository.ProveedorRepository;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
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
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Regresión de las pistas de la auditoría de seguridad (run-1) que se demostraron en el sandbox:
 * adjuntos por la ruta genérica (1), cuentas sembradas (2), login con teléfono largo (6) y fotos con
 * dimensiones enormes (5).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeguridadAuditoriaIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";
    private static final String HASH_SEMBRADO = "$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ArchivoAdjuntoService archivos;

    private String secretaria, jefe, instalador, industrial;
    private UUID cotizacionId;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        instalador = token(RolUsuario.INSTALADOR);
        industrial = token(RolUsuario.INGENIERO_INDUSTRIAL);
    }

    // ---------- utilidades ----------

    private record Resp(int status, JsonNode body, byte[] bytes) {
    }

    private String token(RolUsuario rol) throws Exception {
        String telefono = "7" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
        Usuario u = new Usuario();
        u.setNombre("Seguridad " + rol);
        u.setRol(rol);
        u.setTelefono(telefono);
        u.setPasswordHash(encoder.encode(CLAVE));
        u.setActivo(true);
        usuarios.save(u);
        return "Bearer " + login(telefono, CLAVE, "192.0.2.30").body().get("token").asText();
    }

    private Resp login(String telefono, String clave, String ip) throws Exception {
        return resp(mvc.perform(post("/api/usuarios/login").with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("telefono", telefono, "password", clave)))).andReturn());
    }

    private Resp resp(MvcResult r) throws Exception {
        byte[] bytes = r.getResponse().getContentAsByteArray();
        String texto = new String(bytes, StandardCharsets.UTF_8).trim();
        return new Resp(r.getResponse().getStatus(),
                texto.startsWith("{") || texto.startsWith("[") ? json.readTree(texto) : null, bytes);
    }

    private Resp llamar(MockHttpServletRequestBuilder req, String bearer, Object cuerpo) throws Exception {
        req.header("Authorization", bearer);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return resp(mvc.perform(req).andReturn());
    }

    private static byte[] png(Color color) throws Exception {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** PNG negro escrito a mano: pesa pocos KB aunque declare dimensiones enormes. */
    static byte[] pngGigante(int ancho, int alto) throws Exception {
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(idat, new Deflater(9))) {
            byte[] fila = new byte[1 + ancho * 3];
            for (int y = 0; y < alto; y++) {
                z.write(fila);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(png);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(ancho);
        h.writeInt(alto);
        h.write(new byte[]{8, 2, 0, 0, 0});
        chunk(out, "IHDR", ihdr.toByteArray());
        chunk(out, "IDAT", idat.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(DataOutputStream out, String tipo, byte[] datos) throws Exception {
        byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(datos.length);
        out.write(t);
        out.write(datos);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(datos);
        out.writeInt((int) crc.getValue());
    }

    /** Proyecto con dos tareas PENDIENTES; deja la cotización enviada y aceptada en cotizacionId. */
    private UUID proyecto() throws Exception {
        ProductoCatalogo cam = new ProductoCatalogo();
        cam.setNombre("Cámara seguridad " + SECUENCIA.incrementAndGet());
        cam.setCategoria(CategoriaProducto.SEGURIDAD);
        UUID camara = productos.save(cam).getId();
        ProductoCatalogo cer = new ProductoCatalogo();
        cer.setNombre("Cerradura seguridad " + SECUENCIA.incrementAndGet());
        cer.setCategoria(CategoriaProducto.CONTROL_ACCESO);
        UUID cerradura = productos.save(cer).getId();
        Proveedor prov = new Proveedor();
        prov.setNombre("Proveedor seguridad " + SECUENCIA.incrementAndGet());
        prov.setCategoriasAsociadas("seguridad, control_acceso");
        prov.setContacto("88880000");
        UUID proveedor = proveedores.save(prov).getId();

        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Familia Seguridad", "telefono", "8888-4321", "direccion", "Palmares")));
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Portón", "items", List.of(Map.of("productoId", camara, "cantidad", 1))),
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
        Resp enviada = llamar(post("/api/cotizaciones/" + cid + "/enviar"), secretaria, Map.of("version", cot.get("version").asLong()));
        assertEquals(200, enviada.status(), String.valueOf(enviada.body()));
        // La versión que devuelve el envío sirve para el paso siguiente (el id del PDF no deja una versión vieja).
        Resp aceptada = llamar(post("/api/cotizaciones/" + cid + "/aceptar"), secretaria,
                Map.of("version", enviada.body().get("version").asLong()));
        assertEquals(200, aceptada.status(), String.valueOf(aceptada.body()));
        Resp iniciada = llamar(post("/api/cotizaciones/" + cid + "/iniciar-proyecto"), secretaria,
                Map.of("version", aceptada.body().get("version").asLong()));
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        return UUID.fromString(iniciada.body().get("proyectoId").asText());
    }

    // ---------- pista 1: adjuntos ----------

    @Test
    void laRutaGenericaYaNoSubeNiBorra() throws Exception {
        UUID p = proyecto();
        JsonNode t = llamar(get("/api/proyectos/" + p), jefe, null).body().get("tareas").get(0);
        int subida = mvc.perform(multipart("/api/v1/archivos/upload")
                .file(new MockMultipartFile("file", "x.txt", "text/plain", "hola".getBytes()))
                .param("entidadTipo", "CHECKLIST_TAREA").param("entidadId", t.get("id").asText())
                .param("categoria", "EVIDENCIA_ANTES")
                .header("Authorization", secretaria)).andReturn().getResponse().getStatus();
        assertTrue(subida == 404 || subida == 405, "upload genérico: " + subida);
        assertEquals(0, jdbc.queryForObject("select count(*) from archivos_adjuntos where entidad_id = ?::uuid",
                Integer.class, t.get("id").asText()));

        // Sin evidencia la tarea sigue sin poder completarse.
        assertEquals(409, llamar(put("/api/proyectos/tareas/" + t.get("id").asText() + "/estado"), instalador,
                Map.of("version", t.get("version").asLong(), "estado", "COMPLETADA")).status());

        UUID pdf = jdbc.queryForObject("select pdf_archivo_id from cotizaciones where id = ?", UUID.class, cotizacionId);
        int borrar = llamar(delete("/api/v1/archivos/" + pdf), industrial, null).status();
        assertTrue(borrar == 404 || borrar == 405, "delete genérico: " + borrar);
        assertEquals(0, jdbc.queryForObject("select count(*) from archivos_adjuntos where id = ? and deleted_at is not null",
                Integer.class, pdf));
        assertEquals(404, llamar(get("/api/v1/archivos/entidad/COTIZACION/" + cotizacionId), instalador, null).status());
    }

    @Test
    void elPdfEnviadoSeSirvePorIdYNoLoVenLosRolesDeCampo() throws Exception {
        proyecto();
        UUID pdf = jdbc.queryForObject("select pdf_archivo_id from cotizaciones where id = ?", UUID.class, cotizacionId);
        assertNotNull(pdf, "el envío guarda el id del PDF");
        byte[] original = llamar(get("/api/cotizaciones/" + cotizacionId + "/pdf"), secretaria, null).bytes();

        // Un PDF posterior en la misma ranura (como el que dejaba la ruta genérica) ya no lo reemplaza.
        archivos.guardarGenerado("%PDF-1.4 falso".getBytes(), "falso.pdf", "application/pdf",
                "COTIZACION", cotizacionId, "DOCUMENTO_PDF", null);
        assertArrayEquals(original, llamar(get("/api/cotizaciones/" + cotizacionId + "/pdf"), secretaria, null).bytes());

        // La descarga por id respeta los roles de cotizaciones.
        assertEquals(403, llamar(get("/api/v1/archivos/" + pdf + "/descargar"), instalador, null).status());
        assertEquals(403, llamar(get("/api/v1/archivos/" + pdf + "/descargar"), industrial, null).status());
        Resp deSecretaria = llamar(get("/api/v1/archivos/" + pdf + "/descargar"), secretaria, null);
        assertEquals(200, deSecretaria.status());
        assertTrue(Arrays.equals(original, deSecretaria.bytes()));
    }

    @Test
    void lasFotosSiguenDescargandosePorId() throws Exception {
        UUID p = proyecto();
        String tarea = llamar(get("/api/proyectos/" + p), jefe, null).body().get("tareas").get(0).get("id").asText();
        assertEquals(200, mvc.perform(multipart("/api/proyectos/tareas/" + tarea + "/fotos/ANTES")
                .file(new MockMultipartFile("file", "f.png", "image/png", png(Color.ORANGE)))
                .header("Authorization", instalador)).andReturn().getResponse().getStatus());
        String foto = llamar(get("/api/proyectos/" + p), jefe, null).body().get("tareas").get(0)
                .get("fotosAntes").get(0).asText();
        assertEquals(200, llamar(get("/api/v1/archivos/" + foto + "/descargar"), industrial, null).status());
        assertEquals(200, llamar(get("/api/v1/archivos/" + foto + "/descargar"), instalador, null).status());
    }

    // ---------- pista 2: cuentas sembradas ----------

    @Test
    void lasCuentasSembradasQuedanDesactivadas() throws Exception {
        assertEquals(0, jdbc.queryForObject("select count(*) from usuarios where password_hash = ? and activo",
                Integer.class, HASH_SEMBRADO));
        jdbc.update("delete from intentos_login where telefono = '88888888'");
        if (jdbc.queryForObject("select count(*) from usuarios where telefono = '88888888' and password_hash = ?",
                Integer.class, HASH_SEMBRADO) > 0) {
            assertEquals(401, login("88888888", "123456", "192.0.2.31").status());
        }
    }

    // ---------- pista 6: login ----------

    @Test
    void unTelefonoLargoSeRechazaAntesDeBCryptYNoEvadeElLimitador() throws Exception {
        String largo = "9".repeat(60);
        assertEquals(400, login(largo, "x", "198.51.100.71").status());
        // Formato inválido pero corto: misma respuesta que una clave incorrecta.
        assertEquals(401, login("abc", "x", "198.51.100.71").status());
        assertEquals(400, login("88888888", "x".repeat(201), "198.51.100.71").status());

        // Un teléfono de 8 dígitos inexistente sigue contando y se bloquea al quinto fallo.
        String tel = "1" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(tel, "mala-" + i, "198.51.100.72").status());
        }
        assertEquals(429, login(tel, "mala", "198.51.100.72").status());
        jdbc.update("delete from intentos_login where telefono = ?", tel);
    }

    // ---------- pista 5: foto con dimensiones enormes ----------

    @Test
    void unaFotoConDemasiadosPixelesSeRechaza() throws Exception {
        UUID p = proyecto();
        String tarea = llamar(get("/api/proyectos/" + p), jefe, null).body().get("tareas").get(0).get("id").asText();
        byte[] gigante = pngGigante(12000, 12000);
        assertTrue(gigante.length < 1_000_000, "pesa poco aunque declare 144 MP");
        Resp r = resp(mvc.perform(multipart("/api/proyectos/tareas/" + tarea + "/fotos/ANTES")
                .file(new MockMultipartFile("file", "g.png", "image/png", gigante))
                .header("Authorization", instalador)).andReturn());
        assertEquals(400, r.status());
        assertTrue(r.body().get("message").asText().contains("píxeles"), r.body().toString());
    }
}
