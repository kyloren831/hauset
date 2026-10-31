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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Recorrido completo: lead → informe técnico → cotización (ítems, margen del jefe, IVA) → envío con PDF
 * e historial de precios → aceptación que crea el proyecto con instrucciones v1 y checklist.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CotizacionFlujoIntegrationTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger((int) (System.nanoTime() % 1_000_000));
    private static final String CLAVE = "clave-de-prueba-123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProductoCatalogoRepository productos;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private String secretaria, jefe, instalador, marketing;
    private UUID camara, cerradura, provSeguridad, provIluminacion;

    @BeforeEach
    void preparar() throws Exception {
        secretaria = token(RolUsuario.SECRETARIA);
        jefe = token(RolUsuario.JEFE_SISTEMAS);
        instalador = token(RolUsuario.INSTALADOR);
        marketing = token(RolUsuario.MARKETING);
        camara = producto("Cámara IP prueba", CategoriaProducto.SEGURIDAD);
        cerradura = producto("Cerradura prueba", CategoriaProducto.CONTROL_ACCESO);
        provSeguridad = proveedor("Seguridad prueba", "seguridad, control_acceso");
        provIluminacion = proveedor("Iluminación prueba", "iluminacion");
    }

    // ---------- utilidades ----------

    private String token(RolUsuario rol) throws Exception {
        String telefono = "7" + String.format("%07d", SECUENCIA.incrementAndGet() % 10_000_000);
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
        p.setNombre(nombre);
        p.setCategoria(categoria);
        return productos.save(p).getId();
    }

    private UUID proveedor(String nombre, String categorias) {
        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setCategoriasAsociadas(categorias);
        p.setContacto("88880000");
        return proveedores.save(p).getId();
    }

    private record Resp(int status, JsonNode body) {
    }

    private Resp llamar(MockHttpServletRequestBuilder req, String bearer, Object cuerpo) throws Exception {
        req.header("Authorization", bearer);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(req).andReturn();
        String texto = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode body = texto.isBlank() || !texto.trim().startsWith("{") && !texto.trim().startsWith("[")
                ? null : json.readTree(texto);
        return new Resp(r.getResponse().getStatus(), body);
    }

    /** Lead + informe técnico (2 cámaras en 2 ubicaciones y 1 cerradura) + borrador de cotización. */
    private JsonNode borrador() throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria,
                Map.of("cliente", Map.of("nombre", "Familia Prueba", "telefono", "8888-1234")));
        assertEquals(201, lead.status());
        assertEquals("WHATSAPP", lead.body().get("origen").asText());

        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Portón principal", "items", List.of(
                                Map.of("productoId", camara, "cantidad", 1),
                                Map.of("productoId", cerradura, "cantidad", 1))),
                        Map.of("nota", "Patio trasero", "items", List.of(
                                Map.of("productoId", camara, "cantidad", 1))))));
        assertEquals(201, informe.status());
        // La misma cámara en dos ubicaciones se suma en los totales.
        JsonNode totales = informe.body().get("totales");
        assertEquals(2, totales.size());
        assertEquals(camara.toString(), totales.get(0).get("productoId").asText());
        assertEquals(2, totales.get(0).get("cantidad").asInt());

        Resp cot = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", informe.body().get("id").asText()));
        assertEquals(201, cot.status());
        // Es la v.1; ahora tiene cotización, así que sus fotos ya no cambian y no admite otra cotización.
        String informeId = informe.body().get("id").asText();
        assertEquals(1, informe.body().get("numeroVersion").asInt());
        assertTrue(informe.body().get("fotosEditables").asBoolean());
        JsonNode conCot = llamar(get("/api/informes/" + informeId), jefe, null).body();
        assertFalse(conCot.get("fotosEditables").asBoolean());
        assertEquals(cot.body().get("id").asText(), conCot.get("cotizacion").get("cotizacionId").asText());
        assertEquals(1, cot.body().get("informeVersion").asInt());
        assertEquals(409, llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", informeId)).status());
        // La cotización lleva una sola línea de cámaras con las 2 unidades.
        assertEquals(2, cot.body().get("items").size());
        assertEquals(2, cot.body().get("items").get(0).get("cantidad").asInt());
        return cot.body();
    }

    private JsonNode armarItems(JsonNode cot) throws Exception {
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/items"), secretaria, Map.of(
                "version", cot.get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", camara, "proveedorId", provSeguridad, "cantidad", 2, "precioNeto", 48500),
                        Map.of("productoId", cerradura, "proveedorId", provSeguridad, "cantidad", 1, "precioNeto", 96300))));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    private JsonNode margen(JsonNode cot, int porcentaje) throws Exception {
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/margenes"), jefe,
                Map.of("version", cot.get("version").asLong(), "margenGeneral", porcentaje));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body();
    }

    private List<String> idsPorIniciar() throws Exception {
        Resp r = llamar(get("/api/cotizaciones/por-iniciar"), jefe, null);
        assertEquals(200, r.status());
        List<String> ids = new java.util.ArrayList<>();
        r.body().forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    /** La tarea que se creó sola al aceptar esta cotización, vista por un jefe. */
    private JsonNode tareaAutomatica(String cotizacionId) throws Exception {
        Resp r = llamar(get("/api/tareas-equipo"), jefe, null);
        assertEquals(200, r.status());
        for (JsonNode t : r.body()) {
            if ("COTIZACION_ACEPTADA".equals(t.get("origen").asText())
                    && cotizacionId.equals(t.get("ligada").get("id").asText())) {
                return t;
            }
        }
        return null;
    }

    private Resp accion(JsonNode cot, String accion, String bearer) throws Exception {
        return llamar(post("/api/cotizaciones/" + cot.get("id").asText() + "/" + accion), bearer,
                Map.of("version", cot.get("version").asLong()));
    }

    // ---------- pruebas ----------

    @Test
    void recorridoCompletoHastaCrearElProyecto() throws Exception {
        JsonNode cot = borrador();
        assertEquals("BORRADOR", cot.get("estado").asText());
        assertEquals(2, cot.get("items").size());
        assertEquals(0, cot.get("ivaPorcentaje").decimalValue().compareTo(new java.math.BigDecimal("13")));

        cot = armarItems(cot);
        cot = margen(cot, 30);
        assertNotNull(cot.get("margenDefinidoPor").asText());
        // 2 × (48500 × 1,30) + 1 × (96300 × 1,30) = 126100 + 125190 = 251290; IVA 13 % = 32667,70
        assertEquals(0, cot.get("totalSinIva").decimalValue().compareTo(new java.math.BigDecimal("251290.00")));
        assertEquals(0, cot.get("montoIva").decimalValue().compareTo(new java.math.BigDecimal("32667.70")));
        assertEquals(0, cot.get("totalConIva").decimalValue().compareTo(new java.math.BigDecimal("283957.70")));

        Resp enviada = accion(cot, "enviar", secretaria);
        assertEquals(200, enviada.status(), String.valueOf(enviada.body()));
        cot = enviada.body();
        assertEquals("ENVIADA", cot.get("estado").asText());
        assertTrue(cot.get("numero").asText().matches("COT-\\d{4}-\\d{4}"));

        // RF-CAT-04: cada precio cotizado quedó en el historial, ligado a su ítem.
        Integer enHistorial = jdbc.queryForObject(
                "select count(*) from precios_historial h join cotizacion_items i on i.id = h.cotizacion_item_id "
                        + "where i.cotizacion_id = ?", Integer.class, UUID.fromString(cot.get("id").asText()));
        assertEquals(2, enHistorial);

        // El PDF guardado se puede descargar.
        MvcResult pdf = mvc.perform(get("/api/cotizaciones/" + cot.get("id").asText() + "/pdf")
                .header("Authorization", secretaria)).andReturn();
        assertEquals(200, pdf.getResponse().getStatus());
        assertTrue(new String(pdf.getResponse().getContentAsByteArray(), 0, 5).startsWith("%PDF"));

        // RN-04: una cotización enviada ya no se modifica.
        Resp tarde = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/iva"), secretaria,
                Map.of("version", cot.get("version").asLong(), "porcentaje", 0));
        assertEquals(409, tarde.status());

        Resp aceptada = accion(cot, "aceptar", secretaria);
        assertEquals(200, aceptada.status(), String.valueOf(aceptada.body()));
        assertEquals("ACEPTADA", aceptada.body().get("estado").asText());
        // RN-11: aceptar no crea el proyecto; queda en el aviso de "por iniciar".
        assertTrue(aceptada.body().get("proyectoId").isNull());
        assertTrue(idsPorIniciar().contains(cot.get("id").asText()));
        // RF-COT-18: el aviso llega como tarea pública en la lista de los jefes y la secretaria.
        JsonNode aviso = tareaAutomatica(cot.get("id").asText());
        assertNotNull(aviso, "falta la tarea automática");
        assertEquals("POR_HACER", aviso.get("estado").asText());
        assertFalse(aviso.get("privada").asBoolean());
        assertEquals("COTIZACION", aviso.get("ligada").get("tipo").asText());
        assertTrue(aviso.get("aviso").isNull(), "tiene informe: no falta nada");
        // El instalador no inicia proyectos.
        assertEquals(403, accion(aceptada.body(), "iniciar-proyecto", instalador).status());
        Resp iniciada = accion(aceptada.body(), "iniciar-proyecto", secretaria);
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        // Al iniciar el proyecto, la tarea automática queda hecha.
        assertEquals("HECHA", tareaAutomatica(cot.get("id").asText()).get("estado").asText());
        UUID proyecto = UUID.fromString(iniciada.body().get("proyectoId").asText());
        assertFalse(idsPorIniciar().contains(cot.get("id").asText()));

        // RF-COT-09 / RF-PRY-02: proyecto con instrucciones v1, sus ítems y el checklist.
        assertEquals(1, jdbc.queryForObject("select count(*) from instrucciones_version where proyecto_id = ? "
                + "and numero_version = 1 and generada_desde = 'COTIZACION'", Integer.class, proyecto));
        assertEquals(2, jdbc.queryForObject("select count(*) from instruccion_items ii join instrucciones_version v "
                + "on v.id = ii.instruccion_version_id where v.proyecto_id = ?", Integer.class, proyecto));
        // Una tarea por producto y ubicación: la cámara va en 2 ubicaciones (1 + 1) y la cerradura en 1.
        assertEquals(3, jdbc.queryForObject("select count(*) from checklist_tareas t join instrucciones_version v "
                + "on v.id = t.instruccion_version_id where v.proyecto_id = ? and t.estado = 'PENDIENTE' "
                + "and t.ubicacion_id is not null", Integer.class, proyecto));

        // Aceptar o iniciar dos veces no crea otro proyecto.
        assertEquals(409, accion(iniciada.body(), "aceptar", secretaria).status());
        assertEquals(409, accion(iniciada.body(), "iniciar-proyecto", secretaria).status());

        checklist(proyecto);
    }

    /** RF-PRY-09/10: el instalador completa tareas con fotos de antes y después; no ve precios. */
    private void checklist(UUID proyecto) throws Exception {
        assertEquals(403, llamar(get("/api/proyectos/" + proyecto), marketing, null).status());
        Resp lista = llamar(get("/api/proyectos"), instalador, null);
        assertEquals(200, lista.status());
        Resp det = llamar(get("/api/proyectos/" + proyecto), instalador, null);
        assertEquals(200, det.status());
        assertEquals("COTIZADO", det.body().get("estado").asText());
        assertFalse(det.body().toString().toLowerCase().contains("precio"), "el instalador no ve precios");
        assertEquals(2, det.body().get("ubicaciones").size());
        JsonNode tarea = det.body().get("tareas").get(0);
        assertEquals(1, tarea.get("ubicacionNumero").asInt());
        String tid = tarea.get("id").asText();

        // Sin fotos no se completa; "no aplica" exige nota.
        assertEquals(409, llamar(put("/api/proyectos/tareas/" + tid + "/estado"), instalador,
                Map.of("version", tarea.get("version").asLong(), "estado", "COMPLETADA")).status());
        JsonNode otra = det.body().get("tareas").get(1);
        assertEquals(400, llamar(put("/api/proyectos/tareas/" + otra.get("id").asText() + "/estado"), instalador,
                Map.of("version", otra.get("version").asLong(), "estado", "NO_APLICA")).status());

        var antes = new org.springframework.mock.web.MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2});
        var despues = new org.springframework.mock.web.MockMultipartFile("file", "d.jpg", "image/jpeg", new byte[]{3, 4});
        assertEquals(200, mvc.perform(multipart("/api/proyectos/tareas/" + tid + "/fotos/ANTES").file(antes)
                .header("Authorization", instalador)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(multipart("/api/proyectos/tareas/" + tid + "/fotos/DESPUES").file(despues)
                .header("Authorization", secretaria)).andReturn().getResponse().getStatus());
        MvcResult r = mvc.perform(multipart("/api/proyectos/tareas/" + tid + "/fotos/DESPUES").file(despues)
                .header("Authorization", instalador)).andReturn();
        JsonNode conFotos = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode t0 = conFotos.get("tareas").get(0);
        assertEquals(1, t0.get("fotosAntes").size());
        assertEquals(1, t0.get("fotosDespues").size());

        Resp hecha = llamar(put("/api/proyectos/tareas/" + tid + "/estado"), instalador,
                Map.of("version", t0.get("version").asLong(), "estado", "COMPLETADA", "notas", "Quedó firme"));
        assertEquals(200, hecha.status(), String.valueOf(hecha.body()));
        assertEquals("EN_INSTALACION", hecha.body().get("estado").asText());
        assertEquals("COMPLETADA", hecha.body().get("tareas").get(0).get("estado").asText());
        assertNotNull(hecha.body().get("tareas").get(0).get("completadaPor").asText());
        // Versión vieja → 409; cerrada no admite fotos nuevas.
        assertEquals(409, llamar(put("/api/proyectos/tareas/" + tid + "/estado"), instalador,
                Map.of("version", t0.get("version").asLong(), "estado", "PENDIENTE")).status());
        assertEquals(409, mvc.perform(multipart("/api/proyectos/tareas/" + tid + "/fotos/ANTES").file(antes)
                .header("Authorization", instalador)).andReturn().getResponse().getStatus());

        // Bitácora: proyecto creado, tarea completada y cambio de estado; no se puede borrar.
        List<String> tipos = new java.util.ArrayList<>();
        hecha.body().get("eventos").forEach(e -> tipos.add(e.get("tipo").asText()));
        assertTrue(tipos.containsAll(List.of("PROYECTO_CREADO", "TAREA_COMPLETADA", "ESTADO")), tipos.toString());
        assertThrows(Exception.class, () -> jdbc.update("delete from proyecto_eventos where proyecto_id = ?", proyecto));
    }

    @Test
    void cambiarItemsObligaAQueElJefeVuelvaAConfirmarElMargen() throws Exception {
        JsonNode cot = margen(armarItems(borrador()), 25);
        cot = armarItems(cot);
        assertTrue(cot.get("margenDefinidoPor").isNull());
        Resp r = accion(cot, "enviar", secretaria);
        assertEquals(409, r.status());
        assertTrue(r.body().get("message").asText().contains("jefe"));
    }

    @Test
    void elIvaSePuedeCambiarPorCotizacion() throws Exception {
        JsonNode cot = margen(armarItems(borrador()), 30);
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/iva"), secretaria,
                Map.of("version", cot.get("version").asLong(), "porcentaje", 0));
        assertEquals(200, r.status());
        assertEquals(0, r.body().get("montoIva").decimalValue().signum());
        assertEquals(r.body().get("totalSinIva").decimalValue(), r.body().get("totalConIva").decimalValue());
    }

    @Test
    void soloLosJefesDefinenElMargen() throws Exception {
        JsonNode cot = armarItems(borrador());
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/margenes"), secretaria,
                Map.of("version", cot.get("version").asLong(), "margenGeneral", 30));
        assertEquals(403, r.status());
    }

    @Test
    void noSeEnviaSinProveedorNiPrecio() throws Exception {
        JsonNode cot = borrador();
        assertEquals(409, accion(cot, "enviar", secretaria).status());
    }

    @Test
    void elProveedorDebeAtenderLaCategoriaDelProducto() throws Exception {
        JsonNode cot = borrador();
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/items"), secretaria, Map.of(
                "version", cot.get("version").asLong(),
                "items", List.of(Map.of("productoId", camara, "proveedorId", provIluminacion, "cantidad", 1,
                        "precioNeto", 1000))));
        assertEquals(400, r.status());
    }

    @Test
    void unaVersionVencidaDa409() throws Exception {
        JsonNode cot = borrador();
        armarItems(cot);
        // Se reutiliza la versión anterior a la modificación.
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/iva"), secretaria,
                Map.of("version", cot.get("version").asLong(), "porcentaje", 0));
        assertEquals(409, r.status());
    }

    @Test
    void rechazarExigeMotivoYPermiteVolverACotizarElInforme() throws Exception {
        JsonNode cot = accion(margen(armarItems(borrador()), 30), "enviar", secretaria).body();
        Resp sinMotivo = llamar(post("/api/cotizaciones/" + cot.get("id").asText() + "/rechazar"), secretaria,
                Map.of("version", cot.get("version").asLong(), "motivo", " "));
        assertEquals(400, sinMotivo.status());
        Resp r = llamar(post("/api/cotizaciones/" + cot.get("id").asText() + "/rechazar"), secretaria,
                Map.of("version", cot.get("version").asLong(), "motivo", "Precio alto"));
        assertEquals("RECHAZADA", r.body().get("estado").asText());
        assertTrue(r.body().get("editable").asBoolean());
        String numero = r.body().get("numero").asText();

        // Se corrige el margen: vuelve a borrador con el mismo número y se reenvía como revisión 2.
        JsonNode corregida = margen(r.body(), 22);
        assertEquals("BORRADOR", corregida.get("estado").asText());
        assertEquals(numero, corregida.get("numero").asText());
        assertEquals("Precio alto", corregida.get("motivoRechazo").asText());
        Resp reenviada = accion(corregida, "enviar", secretaria);
        assertEquals(200, reenviada.status(), String.valueOf(reenviada.body()));
        assertEquals("ENVIADA", reenviada.body().get("estado").asText());
        assertEquals(numero, reenviada.body().get("numero").asText());
        assertEquals(2, reenviada.body().get("revision").asInt());
        assertFalse(reenviada.body().get("editable").asBoolean());
        MvcResult pdf = mvc.perform(get("/api/cotizaciones/" + cot.get("id").asText() + "/pdf")
                .header("Authorization", secretaria)).andReturn();
        assertTrue(pdf.getResponse().getHeader("Content-Disposition").contains(numero + "-rev2.pdf"),
                pdf.getResponse().getHeader("Content-Disposition"));
        // Enviada no se edita.
        assertEquals(409, llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/margenes"), jefe,
                Map.of("version", reenviada.body().get("version").asLong(), "margenGeneral", 10)).status());
    }

    @Test
    void unBorradorNuncaEnviadoSeEliminaYElInformeQuedaLibre() throws Exception {
        JsonNode cot = armarItems(borrador());
        String id = cot.get("id").asText();
        String informeId = llamar(get("/api/cotizaciones/" + id), jefe, null).body().get("informeId").asText();

        assertEquals(403, llamar(delete("/api/cotizaciones/" + id), instalador, null).status());
        // Con una versión vieja no se borra (otro la cambió).
        assertEquals(409, llamar(delete("/api/cotizaciones/" + id + "?version=0"), secretaria, null).status());
        Resp r = llamar(delete("/api/cotizaciones/" + id + "?version=" + cot.get("version").asLong()), secretaria, null);
        assertEquals(204, r.status());
        assertEquals(404, llamar(get("/api/cotizaciones/" + id), jefe, null).status());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cotizacion_items WHERE cotizacion_id = ?::uuid",
                Integer.class, id));

        // El informe vuelve a admitir fotos y otra cotización.
        assertTrue(llamar(get("/api/informes/" + informeId), jefe, null).body().get("fotosEditables").asBoolean());
        assertEquals(201, llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", informeId)).status());
    }

    @Test
    void unaCotizacionQueYaSeEnvioNoSeElimina() throws Exception {
        JsonNode enviada = accion(margen(armarItems(borrador()), 30), "enviar", secretaria).body();
        String id = enviada.get("id").asText();
        assertEquals(409, llamar(delete("/api/cotizaciones/" + id), jefe, null).status());

        // Rechazada y corregida vuelve a borrador, pero el cliente ya la recibió: tampoco se elimina.
        JsonNode rechazada = llamar(post("/api/cotizaciones/" + id + "/rechazar"), secretaria,
                Map.of("version", enviada.get("version").asLong(), "motivo", "Precio alto")).body();
        JsonNode corregida = margen(rechazada, 22);
        assertEquals("BORRADOR", corregida.get("estado").asText());
        assertEquals(409, llamar(delete("/api/cotizaciones/" + id), jefe, null).status());
    }

    @Test
    void unLeadPuedeTenerVariasAceptadasYSeEligeCualesIniciar() throws Exception {
        JsonNode primera = borrador();
        String leadId = primera.get("leadId").asText();
        // Otro informe (no otra versión) para el mismo lead.
        Resp otroInforme = llamar(post("/api/informes"), jefe, Map.of("leadId", leadId,
                "ubicaciones", List.of(Map.of("items", List.of(Map.of("productoId", camara, "cantidad", 2),
                        Map.of("productoId", cerradura, "cantidad", 1))))));
        assertEquals(201, otroInforme.status());
        JsonNode segunda = llamar(post("/api/cotizaciones"), secretaria,
                Map.of("informeId", otroInforme.body().get("id").asText())).body();

        JsonNode e1 = accion(margen(armarItems(primera), 30), "enviar", secretaria).body();
        JsonNode e2 = accion(margen(armarItems(segunda), 25), "enviar", secretaria).body();
        JsonNode a1 = accion(e1, "aceptar", secretaria).body();
        JsonNode a2 = accion(e2, "aceptar", secretaria).body();
        assertEquals("ACEPTADA", a1.get("estado").asText());
        assertEquals("ACEPTADA", a2.get("estado").asText());
        assertTrue(idsPorIniciar().containsAll(List.of(a1.get("id").asText(), a2.get("id").asText())));

        // Se inicia solo la segunda (un jefe); la primera sigue aceptada y pendiente de decidir.
        Resp iniciada = accion(a2, "iniciar-proyecto", jefe);
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        List<String> pendientes = idsPorIniciar();
        assertTrue(pendientes.contains(a1.get("id").asText()));
        assertFalse(pendientes.contains(a2.get("id").asText()));
        Resp lista = llamar(get("/api/cotizaciones?estado=ACEPTADA&size=200"), secretaria, null);
        lista.body().get("content").forEach(n -> {
            if (n.get("id").asText().equals(a2.get("id").asText())) {
                assertEquals(iniciada.body().get("proyectoId").asText(), n.get("proyectoId").asText());
            }
        });
    }

    /** Lead nuevo, sin informe. */
    private String lead(String nombre) throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria, Map.of("cliente", Map.of("nombre", nombre, "telefono", "8888-7777")));
        assertEquals(201, lead.status());
        return lead.body().get("id").asText();
    }

    @Test
    void cotizacionEnBlancoSinInformeYElInformeSeLevantaDespues() throws Exception {
        String leadId = lead("Familia En Blanco");
        // Uno de los dos: informe o lead.
        assertEquals(400, llamar(post("/api/cotizaciones"), secretaria, Map.of()).status());
        assertEquals(403, llamar(post("/api/cotizaciones"), instalador, Map.of("leadId", leadId)).status());

        Resp blanco = llamar(post("/api/cotizaciones"), secretaria, Map.of("leadId", leadId));
        assertEquals(201, blanco.status(), String.valueOf(blanco.body()));
        JsonNode cot = blanco.body();
        assertTrue(cot.get("informeId").isNull());
        assertEquals(leadId, cot.get("leadId").asText());
        assertEquals(0, cot.get("items").size());
        assertEquals(409, accion(cot, "enviar", secretaria).status());

        // Se arma a mano, se envía y se acepta sin informe (RN-10).
        cot = margen(armarItems(cot), 30);
        cot = accion(cot, "enviar", secretaria).body();
        assertEquals("COTIZADO", jdbc.queryForObject("select estado from leads where id = ?", String.class,
                UUID.fromString(leadId)));
        cot = accion(cot, "aceptar", secretaria).body();
        assertEquals("ACEPTADA", cot.get("estado").asText());
        // La tarea automática avisa que falta el informe.
        assertTrue(tareaAutomatica(cot.get("id").asText()).get("aviso").asText().contains("informe"));

        // Sin informe no se inicia el proyecto (RF-COT-09).
        Resp sinInforme = accion(cot, "iniciar-proyecto", secretaria);
        assertEquals(409, sinInforme.status());
        assertTrue(sinInforme.body().get("message").asText().contains("informe"), String.valueOf(sinInforme.body()));

        // El jefe de sistemas levanta el informe para esta cotización; precios y márgenes no cambian.
        String total = cot.get("totalConIva").asText();
        Map<String, Object> pedido = Map.of("leadId", leadId, "cotizacionId", cot.get("id").asText(),
                "ubicaciones", List.of(
                        Map.of("nota", "Entrada", "items", List.of(Map.of("productoId", camara, "cantidad", 2))),
                        Map.of("nota", "Puerta", "items", List.of(Map.of("productoId", cerradura, "cantidad", 1)))));
        assertEquals(403, llamar(post("/api/informes"), secretaria, pedido).status());
        Resp informe = llamar(post("/api/informes"), jefe, pedido);
        assertEquals(201, informe.status(), String.valueOf(informe.body()));
        // Ya ligado, pero admite fotos mientras no haya proyecto.
        assertEquals(cot.get("id").asText(), informe.body().get("cotizacion").get("cotizacionId").asText());
        assertTrue(informe.body().get("fotosEditables").asBoolean());
        String informeId = informe.body().get("id").asText();
        String ubi = informe.body().get("ubicaciones").get(0).get("id").asText();
        assertEquals(200, subirFoto(informeId, ubi));

        JsonNode ligada = llamar(get("/api/cotizaciones/" + cot.get("id").asText()), secretaria, null).body();
        assertEquals(informeId, ligada.get("informeId").asText());
        assertEquals(total, ligada.get("totalConIva").asText());
        assertEquals("ACEPTADA", ligada.get("estado").asText());
        // No se le liga un segundo informe.
        assertEquals(409, llamar(post("/api/informes"), jefe, pedido).status());

        Resp iniciada = accion(ligada, "iniciar-proyecto", secretaria);
        assertEquals(200, iniciada.status(), String.valueOf(iniciada.body()));
        UUID proyecto = UUID.fromString(iniciada.body().get("proyectoId").asText());
        assertEquals(2, jdbc.queryForObject("select count(*) from checklist_tareas t join instrucciones_version v "
                + "on v.id = t.instruccion_version_id where v.proyecto_id = ? and t.ubicacion_id is not null",
                Integer.class, proyecto));
        // Con el proyecto iniciado, las fotos del informe ya no cambian.
        assertFalse(llamar(get("/api/informes/" + informeId), jefe, null).body().get("fotosEditables").asBoolean());
        assertEquals(409, subirFoto(informeId, ubi));
    }

    @Test
    void elInformeDeOtroLeadNoSeLigaALaCotizacion() throws Exception {
        JsonNode cot = llamar(post("/api/cotizaciones"), secretaria, Map.of("leadId", lead("Lead A"))).body();
        String otro = lead("Lead B");
        Resp r = llamar(post("/api/informes"), jefe, Map.of("leadId", otro, "cotizacionId", cot.get("id").asText(),
                "ubicaciones", List.of(Map.of("items", List.of(Map.of("productoId", camara, "cantidad", 1))))));
        assertEquals(400, r.status(), String.valueOf(r.body()));
        // No queda un informe suelto: la petición entera se deshizo.
        assertEquals(0, jdbc.queryForObject("select count(*) from informes_tecnicos where lead_id = ?",
                Integer.class, UUID.fromString(otro)));
    }

    @Test
    void elProductoNoDisponibleSeReemplazaYLaTareaVaDondeIbaElOriginal() throws Exception {
        JsonNode cot = borrador();
        UUID alterna = producto("Cámara alterna", CategoriaProducto.SEGURIDAD);
        // La cámara pedida no la tiene el proveedor: se cambia por otra y se recuerda cuál era.
        Resp r = llamar(put("/api/cotizaciones/" + cot.get("id").asText() + "/items"), secretaria, Map.of(
                "version", cot.get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", alterna, "productoOriginalId", camara, "proveedorId", provSeguridad,
                                "cantidad", 2, "precioNeto", 51000),
                        Map.of("productoId", cerradura, "productoOriginalId", cerradura, "proveedorId", provSeguridad,
                                "cantidad", 1, "precioNeto", 96300))));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        JsonNode item = r.body().get("items").get(0);
        assertEquals(alterna.toString(), item.get("productoId").asText());
        assertEquals(camara.toString(), item.get("productoOriginalId").asText());
        assertEquals("Cámara IP prueba", item.get("productoOriginalNombre").asText());
        // Reemplazarse por sí mismo no cuenta como reemplazo.
        assertTrue(r.body().get("items").get(1).get("productoOriginalId").isNull());

        JsonNode aceptada = accion(accion(margen(r.body(), 30), "enviar", secretaria).body(), "aceptar", secretaria).body();
        UUID proyecto = UUID.fromString(accion(aceptada, "iniciar-proyecto", secretaria).body().get("proyectoId").asText());
        // Las 2 cámaras alternas van a las ubicaciones de la cámara original (1 + 1), no a tareas generales.
        assertEquals(2, jdbc.queryForObject("select count(*) from checklist_tareas t join instrucciones_version v "
                + "on v.id = t.instruccion_version_id where v.proyecto_id = ? and t.producto_id = ? "
                + "and t.ubicacion_id is not null", Integer.class, proyecto, alterna));
        assertEquals(0, jdbc.queryForObject("select count(*) from checklist_tareas t join instrucciones_version v "
                + "on v.id = t.instruccion_version_id where v.proyecto_id = ? and t.ubicacion_id is null",
                Integer.class, proyecto));
    }

    private int subirFoto(String informeId, String ubicacionId) throws Exception {
        return mvc.perform(multipart("/api/informes/" + informeId + "/ubicaciones/" + ubicacionId + "/foto")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "f.png", "image/png", png()))
                .header("Authorization", jefe)).andReturn().getResponse().getStatus();
    }

    private static byte[] png() throws Exception {
        var img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void cadaVersionDelInformeTieneSuCotizacion() throws Exception {
        JsonNode v1Cot = borrador();
        String v1 = v1Cot.get("informeId").asText();
        JsonNode v1Inf = llamar(get("/api/informes/" + v1), jefe, null).body();

        // v.2 desde la v.1: otra cámara más; la v.1 y su cotización no cambian.
        Resp v2 = llamar(post("/api/informes/" + v1 + "/versiones"), jefe, Map.of(
                "notas", "Cliente pidió una cámara más",
                "ubicaciones", List.of(
                        Map.of("nota", "Portón principal", "items", List.of(
                                Map.of("productoId", camara, "cantidad", 2),
                                Map.of("productoId", cerradura, "cantidad", 1))),
                        Map.of("nota", "Patio trasero", "items", List.of(
                                Map.of("productoId", camara, "cantidad", 1))))));
        assertEquals(201, v2.status(), String.valueOf(v2.body()));
        assertEquals(2, v2.body().get("numeroVersion").asInt());
        assertEquals(v1Inf.get("grupoId").asText(), v2.body().get("grupoId").asText());
        assertEquals(2, v2.body().get("versiones").size());
        assertEquals(3, v2.body().get("totales").get(0).get("cantidad").asInt());
        assertEquals(2, llamar(get("/api/informes/" + v1), jefe, null).body().get("totales").get(0).get("cantidad").asInt());
        // A una v.1 con cotización no se le cambian fotos.
        String ubiV1 = v1Inf.get("ubicaciones").get(0).get("id").asText();
        assertEquals(409, mvc.perform(multipart("/api/informes/" + v1 + "/ubicaciones/" + ubiV1 + "/foto")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1}))
                .header("Authorization", jefe)).andReturn().getResponse().getStatus());

        // El listado muestra solo la última versión de cada informe.
        Resp lista = llamar(get("/api/informes?size=200"), secretaria, null);
        List<String> ids = new java.util.ArrayList<>();
        lista.body().get("content").forEach(n -> ids.add(n.get("id").asText()));
        assertTrue(ids.contains(v2.body().get("id").asText()));
        assertFalse(ids.contains(v1));

        // Cotización de la v.2; se envían las dos y el cliente acepta ambas (la decisión es cuál iniciar).
        Resp v2Cot = llamar(post("/api/cotizaciones"), secretaria, Map.of("informeId", v2.body().get("id").asText()));
        assertEquals(201, v2Cot.status());
        assertEquals(2, v2Cot.body().get("informeVersion").asInt());
        JsonNode enviada1 = accion(margen(armarItems(v1Cot), 30), "enviar", secretaria).body();
        Resp it2 = llamar(put("/api/cotizaciones/" + v2Cot.body().get("id").asText() + "/items"), secretaria, Map.of(
                "version", v2Cot.body().get("version").asLong(),
                "items", List.of(
                        Map.of("productoId", camara, "proveedorId", provSeguridad, "cantidad", 3, "precioNeto", 48500),
                        Map.of("productoId", cerradura, "proveedorId", provSeguridad, "cantidad", 1, "precioNeto", 96300))));
        JsonNode enviada2 = accion(margen(it2.body(), 30), "enviar", secretaria).body();
        assertEquals(200, accion(enviada1, "aceptar", secretaria).status());
        assertEquals(200, accion(enviada2, "aceptar", secretaria).status());

        // El PDF sigue el formato de la proforma, con los datos de la empresa.
        MvcResult pdf = mvc.perform(get("/api/cotizaciones/" + enviada1.get("id").asText() + "/pdf")
                .header("Authorization", secretaria)).andReturn();
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        java.nio.file.Files.write(java.nio.file.Path.of("target/cotizacion-ejemplo.pdf"), bytes);
        var lector = new com.lowagie.text.pdf.PdfReader(bytes);
        String texto = new com.lowagie.text.pdf.parser.PdfTextExtractor(lector).getTextFromPage(1);
        // (El encabezado se dibuja en cada página y este extractor no lo lee; se revisa el cuerpo.)
        for (String esperado : new String[]{"Receptor:", "Líneas de Detalle", "Total Cotización", "COLONES"}) {
            assertTrue(texto.contains(esperado), "falta en el PDF: " + esperado + "\n" + texto);
        }
        assertFalse(texto.contains("48,500"), "el PDF no debe mostrar el precio neto");

        Resp resumen = llamar(get("/api/cotizaciones?size=200"), secretaria, null);
        boolean conVersion = false;
        for (JsonNode n : resumen.body().get("content")) {
            if (n.get("id").asText().equals(enviada2.get("id").asText())) {
                conVersion = n.get("informeVersion").asInt() == 2 && n.get("revision").asInt() == 1;
            }
        }
        assertTrue(conVersion);
    }

    @Test
    void elInstaladorYMarketingNoVenCotizaciones() throws Exception {
        assertEquals(403, llamar(get("/api/cotizaciones"), instalador, null).status());
        assertEquals(403, llamar(get("/api/cotizaciones"), marketing, null).status());
        assertEquals(403, llamar(get("/api/clientes"), instalador, null).status());
        // Marketing sí consulta el estado de los leads (RF-COT-01 / clases de usuario).
        Resp resumen = llamar(get("/api/leads/resumen"), marketing, null);
        assertEquals(200, resumen.status());
        assertTrue(resumen.body().has("tasaConversion"));
        assertEquals(403, llamar(post("/api/leads"), marketing, Map.of("cliente", Map.of("nombre", "X"))).status());
    }

    @Test
    void soloElIngenieroEnSistemasLevantaElInforme() throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria, Map.of("cliente", Map.of("nombre", "Otro")));
        Resp r = llamar(post("/api/informes"), secretaria, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(Map.of("items", List.of(Map.of("productoId", camara, "cantidad", 1))))));
        assertEquals(403, r.status());
    }

    @Test
    void cadaUbicacionLlevaFotoNotaYVariosProductos() throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria, Map.of("cliente", Map.of("nombre", "Con fotos")));
        String leadId = lead.body().get("id").asText();
        Resp informe = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", leadId,
                "ubicaciones", List.of(
                        Map.of("nota", "Esquina del garaje, a 2.5 m", "items", List.of(
                                Map.of("productoId", camara, "cantidad", 1),
                                Map.of("productoId", cerradura, "cantidad", 1))),
                        Map.of("items", List.of(Map.of("productoId", camara, "cantidad", 1))))));
        assertEquals(201, informe.status());
        String informeId = informe.body().get("id").asText();
        JsonNode garaje = informe.body().get("ubicaciones").get(0);
        assertEquals("Esquina del garaje, a 2.5 m", garaje.get("nota").asText());
        assertEquals(2, garaje.get("items").size());
        assertTrue(garaje.get("fotoId").isNull());
        assertTrue(informe.body().get("ubicaciones").get(1).get("nota").isNull());
        String garajeId = garaje.get("id").asText();
        String ruta = "/api/informes/" + informeId + "/ubicaciones/" + garajeId + "/foto";

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};
        var foto = new org.springframework.mock.web.MockMultipartFile("file", "ubicacion.jpg", "image/jpeg", png);
        MvcResult r = mvc.perform(multipart(ruta).file(foto).header("Authorization", jefe)).andReturn();
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode conFoto = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String fotoId = conFoto.get("ubicaciones").get(0).get("fotoId").asText();
        assertTrue(conFoto.get("ubicaciones").get(1).get("fotoId").isNull());

        // La secretaria la ve al armar la cotización; el instalador no tiene acceso al informe.
        MvcResult descarga = mvc.perform(get("/api/v1/archivos/" + fotoId + "/descargar")
                .header("Authorization", secretaria)).andReturn();
        assertEquals(200, descarga.getResponse().getStatus());
        assertEquals(png.length, descarga.getResponse().getContentAsByteArray().length);
        assertEquals(403, llamar(get("/api/informes/" + informeId), instalador, null).status());

        // Una foto nueva reemplaza a la anterior.
        var otra = new org.springframework.mock.web.MockMultipartFile("file", "b.jpg", "image/jpeg", new byte[]{9, 9, 9});
        r = mvc.perform(multipart(ruta).file(otra).header("Authorization", jefe)).andReturn();
        String nueva = json.readTree(r.getResponse().getContentAsString()).get("ubicaciones").get(0).get("fotoId").asText();
        assertNotEquals(fotoId, nueva);

        // Solo el ingeniero en sistemas sube fotos del informe; solo se aceptan imágenes.
        assertEquals(403, mvc.perform(multipart(ruta).file(otra).header("Authorization", secretaria))
                .andReturn().getResponse().getStatus());
        var pdf = new org.springframework.mock.web.MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1});
        assertEquals(400, mvc.perform(multipart(ruta).file(pdf).header("Authorization", jefe))
                .andReturn().getResponse().getStatus());
        // Una ubicación que no es del informe da 404.
        assertEquals(404, mvc.perform(multipart("/api/informes/" + informeId + "/ubicaciones/" + UUID.randomUUID() + "/foto")
                .file(otra).header("Authorization", jefe)).andReturn().getResponse().getStatus());

        // Versión nueva: la ubicación con `fotoDe` copia esa foto aunque cambien nota y productos;
        // la que no se envía no pasa a la versión nueva.
        Resp corregido = llamar(post("/api/informes/" + informeId + "/versiones"), jefe, Map.of(
                "ubicaciones", List.of(
                        Map.of("fotoDe", garajeId, "nota", "Garaje, sobre el portón",
                                "items", List.of(Map.of("productoId", camara, "cantidad", 2))),
                        Map.of("nota", "Ubicación nueva: patio",
                                "items", List.of(Map.of("productoId", cerradura, "cantidad", 1))))));
        assertEquals(201, corregido.status(), String.valueOf(corregido.body()));
        assertEquals(2, corregido.body().get("numeroVersion").asInt());
        JsonNode unica = corregido.body().get("ubicaciones");
        assertEquals(2, unica.size());
        assertEquals("Ubicación nueva: patio", unica.get(1).get("nota").asText());
        assertTrue(unica.get(1).get("fotoId").isNull());
        assertNotEquals(garajeId, unica.get(0).get("id").asText());
        String copia = unica.get(0).get("fotoId").asText();
        assertNotEquals(nueva, copia, "la versión nueva tiene su propia copia de la foto");
        assertEquals(png.length, 3 + 8);
        assertEquals(3, mvc.perform(get("/api/v1/archivos/" + copia + "/descargar").header("Authorization", jefe))
                .andReturn().getResponse().getContentAsByteArray().length);
        // La v.1 sigue igual.
        assertEquals(nueva, llamar(get("/api/informes/" + informeId), jefe, null).body()
                .get("ubicaciones").get(0).get("fotoId").asText());
        // No se puede copiar una foto de otro informe.
        assertEquals(400, llamar(post("/api/informes/" + informeId + "/versiones"), jefe, Map.of(
                "ubicaciones", List.of(Map.of("fotoDe", UUID.randomUUID(),
                        "items", List.of(Map.of("productoId", camara, "cantidad", 1)))))).status());
        assertEquals("Garaje, sobre el portón", unica.get(0).get("nota").asText());
        assertEquals(1, unica.get(0).get("items").size());
        assertEquals(2, unica.get(0).get("items").get(0).get("cantidad").asInt());
    }

    @Test
    void unProductoNoSeRepiteDentroDeLaMismaUbicacion() throws Exception {
        Resp lead = llamar(post("/api/leads"), secretaria, Map.of("cliente", Map.of("nombre", "Duplicado")));
        Resp r = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(Map.of("items", List.of(
                        Map.of("productoId", camara, "cantidad", 1),
                        Map.of("productoId", camara, "cantidad", 2))))));
        assertEquals(400, r.status());
        Resp vacia = llamar(post("/api/informes"), jefe, Map.of(
                "leadId", lead.body().get("id").asText(),
                "ubicaciones", List.of(Map.of("nota", "Sin productos", "items", List.of()))));
        assertEquals(400, vacia.status());
    }

    @Test
    void elHistorialDePreciosNoSePuedeModificarNiBorrar() {
        Exception e = assertThrows(Exception.class,
                () -> jdbc.update("update precios_historial set precio_neto = 1 where precio_neto > 0"));
        assertTrue(e.getMessage().contains("solo inserción"));
        assertThrows(Exception.class, () -> jdbc.update("delete from precios_historial"));
    }
}
