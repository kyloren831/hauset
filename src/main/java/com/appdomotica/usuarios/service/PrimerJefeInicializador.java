package com.appdomotica.usuarios.service;

import com.appdomotica.usuarios.dto.PerfilRequests;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import com.appdomotica.usuarios.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una base nueva ya no trae cuentas utilizables (V26 desactiva las sembradas), así que el primer jefe
 * se crea al arrancar con HAUSET_PRIMER_JEFE_TELEFONO, _NOMBRE y _PASSWORD. Solo actúa si no hay
 * ningún jefe activo; después se administra todo desde la app y las variables deben quitarse.
 */
@Slf4j
@Component
public class PrimerJefeInicializador implements ApplicationRunner {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String telefono;
    private final String nombre;
    private final String password;

    public PrimerJefeInicializador(UsuarioRepository usuarios, PasswordEncoder encoder,
                                   @Value("${hauset.primer-jefe.telefono:}") String telefono,
                                   @Value("${hauset.primer-jefe.nombre:}") String nombre,
                                   @Value("${hauset.primer-jefe.password:}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.telefono = telefono.trim();
        this.nombre = nombre.trim();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean configurado = !telefono.isEmpty() || !password.isEmpty();
        if (usuarios.countByRolInAndActivoTrue(UsuarioService.JEFES) > 0) {
            if (configurado) {
                log.warn("Ya hay un jefe activo: se ignoran HAUSET_PRIMER_JEFE_*; conviene quitarlas del entorno");
            }
            return;
        }
        if (!configurado) {
            log.warn("No hay ningún jefe activo: nadie puede administrar cuentas. "
                    + "Define HAUSET_PRIMER_JEFE_TELEFONO, HAUSET_PRIMER_JEFE_NOMBRE y HAUSET_PRIMER_JEFE_PASSWORD y reinicia");
            return;
        }
        if (!telefono.matches(PerfilRequests.TELEFONO)) {
            throw new IllegalStateException("HAUSET_PRIMER_JEFE_TELEFONO: " + PerfilRequests.TELEFONO_MENSAJE);
        }
        if (password.length() < PerfilRequests.PASSWORD_MIN || password.length() > PerfilRequests.PASSWORD_MAX) {
            throw new IllegalStateException("HAUSET_PRIMER_JEFE_PASSWORD: " + PerfilRequests.PASSWORD_MENSAJE);
        }
        if (usuarios.existsByTelefono(telefono)) {
            throw new IllegalStateException("HAUSET_PRIMER_JEFE_TELEFONO ya pertenece a otra cuenta; usa otro número");
        }
        Usuario jefe = new Usuario();
        jefe.setNombre(nombre.isEmpty() ? "Jefe de sistemas" : nombre);
        jefe.setRol(RolUsuario.JEFE_SISTEMAS);
        jefe.setTelefono(telefono);
        jefe.setPasswordHash(encoder.encode(password));
        jefe.setActivo(true);
        usuarios.save(jefe);
        log.info("Primer jefe creado con el teléfono {}; ya se pueden quitar HAUSET_PRIMER_JEFE_*", telefono);
    }
}
