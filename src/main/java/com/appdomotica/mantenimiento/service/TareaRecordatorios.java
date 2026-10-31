package com.appdomotica.mantenimiento.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Tarea diaria de recordatorios (RF-MAN-07): 6:00 en Costa Rica y también al arrancar (por si estuvo apagado). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TareaRecordatorios {
    private final RecordatoriosService service;

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Costa_Rica")
    public void diaria() {
        correr();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        correr();
    }

    private void correr() {
        try {
            int creados = service.generar(LocalDate.now(RecordatoriosService.CR));
            if (creados > 0) {
                log.info("Recordatorios de mantenimiento creados: {}", creados);
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron crear los recordatorios de mantenimiento", e);
        }
    }
}
