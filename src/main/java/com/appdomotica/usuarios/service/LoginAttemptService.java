package com.appdomotica.usuarios.service;

import com.appdomotica.usuarios.entity.IntentoLogin;
import com.appdomotica.usuarios.repository.IntentoLoginRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Limita la fuerza bruta contando fallos recientes por teléfono y por IP, guardados en la base. */
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

    public void registrar(String telefono, String ip, boolean exitoso) {
        repository.save(new IntentoLogin(telefono, ip, exitoso));
    }

    public long segundosDeEspera() {
        return ventana.toSeconds();
    }
}
