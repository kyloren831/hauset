package com.appdomotica.agenda.service;

import com.appdomotica.agenda.dto.AgendaDtos.Agenda;
import com.appdomotica.agenda.dto.AgendaDtos.VisitaAgenda;
import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.proyectos.entity.EstadoVisita;
import com.appdomotica.proyectos.entity.Visita;
import com.appdomotica.proyectos.repository.VisitaRepository;
import com.appdomotica.usuarios.entity.RolUsuario;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agenda compartida de visitas (RF-AGE-01). Los jefes, el ingeniero industrial y la secretaria ven toda la
 * agenda y los retornos por agendar; el instalador solo sus visitas (RF-AGE-05). El filtro se aplica aquí,
 * no en la app. Los días se cuentan en hora de Costa Rica.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {
    static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");
    static final int DIAS_MAXIMOS = 62;

    private final VisitaRepository visitas;

    @Transactional(readOnly = true)
    public Agenda agenda(Usuario quien, LocalDate desde, LocalDate hasta, UUID personaId) {
        LocalDate d = desde != null ? desde : LocalDate.now(ZONA);
        LocalDate h = hasta != null ? hasta : d.plusDays(6);
        if (h.isBefore(d)) {
            throw new BadRequestException("La fecha final es anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(d, h) >= DIAS_MAXIMOS) {
            throw new BadRequestException("Pide como máximo " + DIAS_MAXIMOS + " días a la vez");
        }
        boolean todo = veTodo(quien);
        // Solo las propias para el instalador, aunque pida las de otra persona.
        UUID persona = todo ? personaId : quien.getId();

        List<Visita> rango = visitas.deEntre(inicioDe(d), inicioDe(h.plusDays(1))).stream()
                .filter(v -> persona == null || v.getAsignadoA() != null && v.getAsignadoA().getId().equals(persona))
                .toList();
        Set<UUID> conCruce = conCruce(rango);
        List<VisitaAgenda> lista = rango.stream().map(v -> dto(v, conCruce.contains(v.getId()))).toList();
        List<VisitaAgenda> pendientes = todo
                ? visitas.porAgendar().stream().map(v -> dto(v, false)).toList()
                : List.of();
        return new Agenda(d, h, todo, lista, pendientes);
    }

    /** Todos menos el instalador (marketing ni siquiera entra al endpoint). */
    static boolean veTodo(Usuario u) {
        return u.getRol() != RolUsuario.INSTALADOR;
    }

    /** Visitas agendadas que se cruzan con otra agendada de la misma persona. */
    static Set<UUID> conCruce(List<Visita> lista) {
        List<Visita> agendadas = lista.stream()
                .filter(v -> v.getEstado() == EstadoVisita.AGENDADA && v.getAsignadoA() != null)
                .toList();
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < agendadas.size(); i++) {
            for (int j = i + 1; j < agendadas.size(); j++) {
                Visita a = agendadas.get(i);
                Visita b = agendadas.get(j);
                if (Objects.equals(a.getAsignadoA().getId(), b.getAsignadoA().getId())
                        && a.getFechaHora().isBefore(finAgendado(b)) && b.getFechaHora().isBefore(finAgendado(a))) {
                    ids.add(a.getId());
                    ids.add(b.getId());
                }
            }
        }
        return ids;
    }

    private static OffsetDateTime finAgendado(Visita v) {
        return v.getFechaHora().plusMinutes(v.getDuracionMinutos());
    }

    private static OffsetDateTime inicioDe(LocalDate dia) {
        return dia.atStartOfDay(ZONA).toOffsetDateTime();
    }

    private static VisitaAgenda dto(Visita v, boolean cruce) {
        var p = v.getProyecto();
        var cliente = p.getCotizacion().getCliente();
        OffsetDateTime inicio = v.getIniciadaEn() != null ? v.getIniciadaEn() : v.getFechaHora();
        OffsetDateTime fin = v.getTerminadaEn() != null ? v.getTerminadaEn()
                : inicio != null ? inicio.plusMinutes(v.getDuracionMinutos()) : null;
        return new VisitaAgenda(v.getId(), p.getId(), p.getEstado(), v.getNumeroVisita(), v.getEstado(),
                v.getAsignadoA() != null ? v.getAsignadoA().getId() : null,
                v.getAsignadoA() != null ? v.getAsignadoA().getNombre() : null, inicio, fin,
                v.getDuracionMinutos(), v.getMotivo(), cliente.getNombre(), cliente.getDireccion(),
                cliente.getTelefono(), cruce, v.getVersion());
    }
}
