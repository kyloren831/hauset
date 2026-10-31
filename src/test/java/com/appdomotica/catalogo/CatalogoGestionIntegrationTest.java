package com.appdomotica.catalogo;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Gestión del catálogo: estados (activo, inactivo, archivado), filtros, permisos y fotos de producto. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoGestionIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;

    private String jefe, industrial, secretaria, instalador, marketing;
    /** Sufijo para que los nombres no choquen con los de corridas anteriores. */
    private String sufijo;

    @BeforeEach
    void preparar() throws Exception {
        jefe = token(RolUsuario.JEFE_ELECTROMECANICO);
        industrial = token(RolUsuario.INGENIERO_INDUSTRIAL);
        secretaria = token(RolUsuario.SECRETARIA);
        instalador = token(RolUsuario.INSTALADOR);
        marketing = token(RolUsuario.MARKETING);
        sufijo = " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void elProveedorSeDesactivaSeFiltraSeArchivaYSeRestaura() throws Exception {
        String nombre = "Distribuidora" + sufijo;
        Resp creado = llamar(post("/api/proveedores"), secretaria, Map.of(
                "nombre", nombre, "contacto", "2222-3333", "categoriasAsociadas", "Seguridad, CONTROL_ACCESO, redes"));
        assertEquals(201, creado.status());
        String id = creado.body().get("id").asText();
        assertEquals("ACTIVO", creado.body().get("estado").asText());
        assertEquals("seguridad, control_acceso, redes", creado.body().get("categoriasAsociadas").asText());

        // Categoría inexistente y nombre repetido.
        assertEquals(400, llamar(post("/api/proveedores"), secretaria, Map.of(
                "nombre", "Otro" + sufijo, "contacto", "1", "categoriasAsociadas", "jardineria")).status());
        assertEquals(409, llamar(post("/api/proveedores"), secretaria, Map.of(
                "nombre", nombre.toUpperCase(), "contacto", "1")).status());

        // Editar.
        Resp editado = llamar(put("/api/proveedores/" + id), secretaria, Map.of(
                "nombre", nombre, "contacto", "8888-0000", "categoriasAsociadas", "iluminacion"));
        assertEquals(200, editado.status());
        assertEquals("8888-0000", editado.body().get("contacto").asText());

        // Desactivar: deja de salir por defecto, sale con el filtro.
        assertEquals("INACTIVO", llamar(put("/api/proveedores/" + id + "/estado"), secretaria,
                Map.of("estado", "INACTIVO")).body().get("estado").asText());
        assertFalse(nombresProveedores("ACTIVO").contains(nombre));
        assertTrue(nombresProveedores("INACTIVO").contains(nombre));
        assertTrue(nombresProveedores("TODOS").contains(nombre));
        assertEquals(200, llamar(get("/api/proveedores/" + id), instalador, null).status());

        // Archivar: solo jefes; el archivado no se edita hasta restaurarlo.
        assertEquals(403, llamar(post("/api/proveedores/" + id + "/archivar"), secretaria, null).status());
        assertEquals("ARCHIVADO", llamar(post("/api/proveedores/" + id + "/archivar"), jefe, null)
                .body().get("estado").asText());
        assertTrue(nombresProveedores("ARCHIVADO").contains(nombre));
        assertFalse(nombresProveedores("INACTIVO").contains(nombre));
        assertEquals(409, llamar(put("/api/proveedores/" + id), secretaria, Map.of(
                "nombre", nombre, "contacto", "1")).status());
        assertEquals(409, llamar(put("/api/proveedores/" + id + "/estado"), secretaria,
                Map.of("estado", "ACTIVO")).status());
        assertEquals("ACTIVO", llamar(post("/api/proveedores/" + id + "/restaurar"), jefe, null)
                .body().get("estado").asText());
        assertTrue(nombresProveedores("ACTIVO").contains(nombre));
    }

    @Test
    void elProductoTieneEstadosYPermisos() throws Exception {
        String nombre = "Sensor de humo" + sufijo;
        Resp creado = llamar(post("/api/catalogo/productos"), industrial, Map.of(
                "nombre", nombre, "categoria", "SEGURIDAD", "especificacion", "Fotoeléctrico"));
        assertEquals(201, creado.status());
        String id = creado.body().get("id").asText();
        assertEquals(0, creado.body().get("fotoIds").size());

        // La secretaria y el instalador no editan productos; marketing no ve el catálogo.
        assertEquals(403, llamar(post("/api/catalogo/productos"), secretaria, Map.of(
                "nombre", "X" + sufijo, "categoria", "SEGURIDAD")).status());
        assertEquals(403, llamar(get("/api/catalogo/productos"), marketing, null).status());
        assertEquals(200, llamar(get("/api/catalogo/productos"), instalador, null).status());

        Resp editado = llamar(put("/api/catalogo/productos/" + id), industrial, Map.of(
                "nombre", nombre, "categoria", "SEGURIDAD", "especificacion", "Fotoeléctrico, 9 V"));
        assertEquals("Fotoeléctrico, 9 V", editado.body().get("especificacion").asText());

        // Búsqueda también por especificación; filtro por estado.
        assertTrue(nombresProductos("ACTIVO", "fotoeléctrico").contains(nombre));
        llamar(put("/api/catalogo/productos/" + id + "/estado"), industrial, Map.of("estado", "INACTIVO"));
        assertFalse(nombresProductos("ACTIVO", nombre).contains(nombre));
        assertTrue(nombresProductos("INACTIVO", nombre).contains(nombre));

        // El ingeniero industrial no archiva; el jefe sí. DELETE también archiva.
        assertEquals(403, llamar(post("/api/catalogo/productos/" + id + "/archivar"), industrial, null).status());
        assertEquals("ARCHIVADO", llamar(delete("/api/catalogo/productos/" + id), jefe, null)
                .body().get("estado").asText());
        assertTrue(nombresProductos("ARCHIVADO", nombre).contains(nombre));

        // Con el original archivado, el nombre queda libre; restaurar el viejo choca con el nuevo.
        assertEquals(201, llamar(post("/api/catalogo/productos"), industrial, Map.of(
                "nombre", nombre, "categoria", "SEGURIDAD")).status());
        assertEquals(409, llamar(post("/api/catalogo/productos/" + id + "/restaurar"), jefe, null).status());
    }

    @Test
    void unProductoInactivoNoEntraEnUnInformeNuevo() throws Exception {
        String id = llamar(post("/api/catalogo/productos"), industrial, Map.of(
                "nombre", "Parlante" + sufijo, "categoria", "AUDIO_AUTOMATIZACION")).body().get("id").asText();
        assertEquals(201, llamar(post("/api/catalogo/productos"), industrial, Map.of(
                "nombre", "Switch PoE 8 puertos" + sufijo, "categoria", "REDES")).status());
        llamar(put("/api/catalogo/productos/" + id + "/estado"), industrial, Map.of("estado", "INACTIVO"));
        String jefeSistemas = token(RolUsuario.JEFE_SISTEMAS);
        Resp lead = llamar(post("/api/leads"), secretaria, Map.of("cliente", Map.of("nombre", "Cliente" + sufijo)));
        Resp informe = llamar(post("/api/informes"), jefeSistemas, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(Map.of("items", List.of(Map.of("productoId", id, "cantidad", 1))))));
        assertEquals(400, informe.status());
    }

    @Test
    void elProductoLlevaVariasFotos() throws Exception {
        String id = llamar(post("/api/catalogo/productos"), industrial, Map.of(
                "nombre", "Cámara bala" + sufijo, "categoria", "SEGURIDAD")).body().get("id").asText();
        String ruta = "/api/catalogo/productos/" + id + "/fotos";

        JsonNode r1 = subir(ruta, industrial, new byte[]{1, 2, 3}, "image/jpeg", 200);
        JsonNode r2 = subir(ruta, industrial, new byte[]{4, 5, 6}, "image/png", 200);
        assertEquals(1, r1.get("fotoIds").size());
        assertEquals(2, r2.get("fotoIds").size());
        String primera = r2.get("fotoIds").get(0).asText();
        assertEquals(r1.get("fotoIds").get(0).asText(), primera, "la primera sigue siendo la principal");

        // El listado trae las fotos; el instalador las puede ver.
        Resp lista = llamar(get("/api/catalogo/productos?estado=TODOS&search=" + sufijo.trim()), instalador, null);
        assertEquals(2, lista.body().get("content").get(0).get("fotoIds").size());
        assertEquals(200, mvc.perform(get("/api/v1/archivos/" + primera + "/descargar")
                .header("Authorization", instalador)).andReturn().getResponse().getStatus());

        // Solo imágenes; solo editores.
        subir(ruta, industrial, new byte[]{7}, "application/pdf", 400);
        subir(ruta, secretaria, new byte[]{8}, "image/jpeg", 403);

        // Quitar una foto.
        Resp sinPrimera = llamar(delete(ruta + "/" + primera), industrial, null);
        assertEquals(200, sinPrimera.status());
        assertEquals(1, sinPrimera.body().get("fotoIds").size());
        assertEquals(404, llamar(delete(ruta + "/" + UUID.randomUUID()), industrial, null).status());

        // Archivado: no se le agregan fotos.
        llamar(post("/api/catalogo/productos/" + id + "/archivar"), jefe, null);
        subir(ruta, industrial, new byte[]{9}, "image/jpeg", 409);
    }

    // ---------- utilidades ----------

    private JsonNode subir(String ruta, String bearer, byte[] bytes, String tipo, int esperado) throws Exception {
        MvcResult r = mvc.perform(multipart(ruta).file(new MockMultipartFile("file", "f", tipo, bytes))
                .header("Authorization", bearer)).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(esperado, r.getResponse().getStatus(), texto);
        return esperado == 200 ? json.readTree(texto) : null;
    }

    private List<String> nombresProveedores(String estado) throws Exception {
        return nombres(llamar(get("/api/proveedores?size=200&estado=" + estado + "&search=" + sufijo.trim()),
                secretaria, null));
    }

    private List<String> nombresProductos(String estado, String busqueda) throws Exception {
        return nombres(llamar(get("/api/catalogo/productos").param("size", "200").param("estado", estado)
                .param("search", busqueda), instalador, null));
    }

    private List<String> nombres(Resp r) {
        assertEquals(200, r.status());
        List<String> lista = new ArrayList<>();
        r.body().get("content").forEach(n -> lista.add(n.get("nombre").asText()));
        return lista;
    }

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

    private Resp llamar(MockHttpServletRequestBuilder req, String bearer, Object cuerpo) throws Exception {
        req.header("Authorization", bearer);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(req).andReturn();
        String texto = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = texto.isBlank() || !texto.trim().startsWith("{") ? null : json.readTree(texto);
        return new Resp(r.getResponse().getStatus(), body);
    }

    private record Resp(int status, JsonNode body) {
    }
}
