package com.appdomotica.common.idempotencia;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra las claves de idempotencia de más de 48 horas (los reintentos ocurren en minutos). */
@Component
@RequiredArgsConstructor
public class LimpiezaIdempotencia {
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void limpiar() {
        jdbc.update("delete from solicitudes_idempotentes where creada_en < now() - interval '48 hours'");
    }
}
