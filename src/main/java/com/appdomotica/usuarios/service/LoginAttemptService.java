package com.appdomotica.usuarios.service;

import com.appdomotica.usuarios.entity.IntentoLogin;
import com.appdomotica.usuarios.repository.IntentoLoginRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Limita la fuerza bruta contando fallos recientes por teléfono y por IP, guardados en la base. */
@Slf4j
@Service
public class LoginAttemptService {
    private final IntentoLoginRepository repository;
    private final int maxFallosPorTelefono;
    private final int maxFallosPorIp;
    private final Duration ventana;

    public LoginAttemptService(IntentoLoginRepository repository,
                               @Value("${app.login.maxFallosPorTelefono:5}") int maxFallosPorTelefono,
                               @Value("${app.login.maxFallosPorIp:20}") int maxFallosPorIp,
                               @Value("${app.login.ventanaMinutos:15}") long ventanaMinutos) {
        this.repository = repository;
        this.maxFallosPorTelefono = maxFallosPorTelefono;
        this.maxFallosPorIp = maxFallosPorIp;
        this.ventana = Duration.ofMinutes(ventanaMinutos);
    }

    public boolean bloqueado(String telefono, String ip) {
        Instant desde = Instant.now().minus(ventana);
        return repository.countByTelefonoAndExitosoFalseAndFechaAfter(telefono, desde) >= maxFallosPorTelefono
                || (ip != null && repository.countByIpAndExitosoFalseAndFechaAfter(ip, desde) >= maxFallosPorIp);
    }

    /** Nunca falla: si el intento no se pudiera guardar, el limitador dejaría de contarlo. */
    public void registrar(String telefono, String ip, boolean exitoso) {
        try {
            repository.save(new IntentoLogin(recortar(telefono, 50), recortar(ip, 64), exitoso));
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el intento de login", e);
        }
    }

    private static String recortar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }

    public long segundosDeEspera() {
        return ventana.toSeconds();
    }
}
