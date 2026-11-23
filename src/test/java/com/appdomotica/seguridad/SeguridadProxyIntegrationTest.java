package com.appdomotica.seguridad;

import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La IP del limitador de login solo sale de X-Forwarded-For si la conexión viene de un proxy local
 * (auditoría, pista 7). Tomcat real: las cabeceras pasan por RemoteIpValve como en DEV, stage y producción.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeguridadProxyIntegrationTest {

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Value("${server.tomcat.remoteip.internal-proxies}") private String proxiesDeConfianza;
    @Value("${server.forward-headers-strategy}") private String estrategia;

    private final HttpClient http = HttpClient.newHttpClient();

    private int login(String telefono, String cabecera, String valor) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/usuarios/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"telefono\":\"" + telefono + "\",\"password\":\"mala\"}"));
        if (cabecera != null) {
            b.header(cabecera, valor);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String ipRegistrada(String telefono) {
        return jdbc.queryForObject("select ip from intentos_login where telefono = ? order by fecha desc limit 1",
                String.class, telefono);
    }

    private static String telefono() {
        return "2" + String.format("%07d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000L));
    }

    @Test
    void soloSeConfiaEnProxiesDeLaMismaMaquina() {
        assertEquals("native", estrategia, "framework confiaría en Forwarded y X-Forwarded-Prefix de cualquiera");
        Pattern p = Pattern.compile(proxiesDeConfianza);
        assertTrue(p.matcher("127.0.0.1").matches());
        assertTrue(p.matcher("0:0:0:0:0:0:0:1").matches());
        // Celulares por Tailscale (DEV), redes privadas y Docker: clientes, no proxies.
        for (String cliente : new String[]{"100.72.170.33", "100.64.0.1", "10.0.0.5", "192.168.1.20", "172.17.0.1", "127.0.0.2x"}) {
            assertFalse(p.matcher(cliente).matches(), cliente);
        }
    }

    @Test
    void desdeElProxyLocalSeUsaXForwardedForPeroSeIgnoraForwarded() throws Exception {
        String tel = telefono();
        Usuario u = new Usuario();
        u.setNombre("Proxy prueba");
        u.setRol(RolUsuario.SECRETARIA);
        u.setTelefono(tel);
        u.setPasswordHash(encoder.encode("clave-de-prueba-123"));
        u.setActivo(true);
        usuarios.save(u);

        // Caddy reemplaza X-Forwarded-For por la IP real: desde 127.0.0.1 es la que vale.
        assertEquals(401, login(tel, "X-Forwarded-For", "203.0.113.50"));
        assertEquals("203.0.113.50", ipRegistrada(tel));

        // Forwarded la deja pasar Caddy tal cual; con native Tomcat no la usa.
        assertEquals(401, login(tel, "Forwarded", "for=203.0.113.51"));
        assertNotEquals("203.0.113.51", ipRegistrada(tel));
        jdbc.update("delete from intentos_login where telefono = ?", tel);
    }
}
