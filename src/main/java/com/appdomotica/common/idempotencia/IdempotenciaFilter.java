package com.appdomotica.common.idempotencia;

import com.appdomotica.config.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Idempotencia para reintentos con mala señal. Si una petición que modifica datos trae
 * {@code Idempotency-Key}, la primera se ejecuta y su respuesta se guarda; una repetición con la
 * misma clave (del mismo usuario y para la misma operación) recibe la respuesta guardada sin
 * volver a ejecutarse. Si la primera todavía se está procesando, la repetición recibe 409.
 * Una respuesta 5xx no se guarda: el cliente puede reintentar.
 */
public class IdempotenciaFilter extends OncePerRequestFilter {
    public static final String CABECERA = "Idempotency-Key";
    public static final String CABECERA_REPETIDA = "Idempotent-Replayed";
    /** La misma operación todavía se está procesando: el cliente debe esperar y reintentar. */
    public static final String CABECERA_EN_PROCESO = "Idempotency-In-Progress";
    private static final Set<String> METODOS = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** Una solicitud "en proceso" más vieja que esto se considera abandonada (el servidor se cayó). */
    private static final long SEGUNDOS_ABANDONADA = 120;

    private final JdbcTemplate jdbc;

    public IdempotenciaFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ver una contraseña no se guarda ni se repite: la respuesta quedaría en claro en la base y una
     * repetición la entregaría sin dejar registro en la bitácora (RF-MAN-04/06).
     */
    private static final java.util.regex.Pattern SIN_GUARDAR =
            java.util.regex.Pattern.compile("^/api/credenciales/[^/]+/revelar/?$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS.contains(request.getMethod()) || request.getHeader(CABECERA) == null
                || SIN_GUARDAR.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID usuario = usuarioActual();
        String clave = request.getHeader(CABECERA).trim();
        if (usuario == null) {
            chain.doFilter(request, response);
            return;
        }
        if (clave.isEmpty() || clave.length() > 100) {
            error(response, 400, "La cabecera Idempotency-Key debe tener entre 1 y 100 caracteres");
            return;
        }
        String huella = (request.getMethod() + " " + request.getRequestURI());
        huella = huella.length() > 300 ? huella.substring(0, 300) : huella;

        if (!reservar(usuario, clave, huella)) {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "select huella, estado, http_status, content_type, cuerpo, creada_en "
                            + "from solicitudes_idempotentes where usuario_id = ? and clave = ?", usuario, clave);
            if (filas.isEmpty()) {
                // Se borró entre medio (expiró o falló): se reintenta la reserva una vez.
                if (!reservar(usuario, clave, huella)) {
                    enProceso(response);
                    return;
                }
            } else {
                Map<String, Object> f = filas.get(0);
                if (!huella.equals(f.get("huella"))) {
                    error(response, 422, "Esta clave de idempotencia ya se usó para otra operación");
                    return;
                }
                if ("COMPLETADA".equals(f.get("estado"))) {
                    repetir(response, f);
                    return;
                }
                OffsetDateTime creada = ((java.sql.Timestamp) f.get("creada_en")).toInstant()
                        .atOffset(java.time.ZoneOffset.UTC);
                if (creada.isAfter(OffsetDateTime.now().minusSeconds(SEGUNDOS_ABANDONADA))) {
                    enProceso(response);
                    return;
                }
                // Abandonada: se toma de nuevo.
                jdbc.update("update solicitudes_idempotentes set creada_en = now() where usuario_id = ? and clave = ?",
                        usuario, clave);
            }
        }

        ContentCachingResponseWrapper envoltura = new ContentCachingResponseWrapper(response);
        boolean guardada = false;
        try {
            chain.doFilter(request, envoltura);
            int status = envoltura.getStatus();
            if (status < 500) {
                jdbc.update("update solicitudes_idempotentes set estado = 'COMPLETADA', http_status = ?, "
                                + "content_type = ?, cuerpo = ? where usuario_id = ? and clave = ?",
                        status, envoltura.getContentType(), envoltura.getContentAsByteArray(), usuario, clave);
                guardada = true;
            }
        } finally {
            if (!guardada) {
                jdbc.update("delete from solicitudes_idempotentes where usuario_id = ? and clave = ?", usuario, clave);
            }
            envoltura.copyBodyToResponse();
        }
    }

    /** Inserta la clave como "en proceso". False si ya existía. */
    private boolean reservar(UUID usuario, String clave, String huella) {
        return jdbc.update("insert into solicitudes_idempotentes (usuario_id, clave, huella, estado) "
                + "values (?, ?, ?, 'EN_PROCESO') on conflict do nothing", usuario, clave, huella) == 1;
    }

    private static void repetir(HttpServletResponse response, Map<String, Object> f) throws IOException {
        response.setStatus(((Number) f.get("http_status")).intValue());
        if (f.get("content_type") != null) {
            response.setContentType((String) f.get("content_type"));
        }
        response.setHeader(CABECERA_REPETIDA, "true");
        byte[] cuerpo = (byte[]) f.get("cuerpo");
        if (cuerpo != null) {
            response.getOutputStream().write(cuerpo);
        }
    }

    private static void enProceso(HttpServletResponse response) throws IOException {
        response.setHeader(CABECERA_EN_PROCESO, "true");
        error(response, 409, "La operación se está procesando; intenta de nuevo en unos segundos");
    }

    private static void error(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"error\":\"Idempotencia\",\"mensaje\":\"" + mensaje + "\"}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static UUID usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CustomUserDetails u) {
            return u.getUsuario().getId();
        }
        return null;
    }
}
