package com.appdomotica.mantenimiento.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.common.exception.ResourceNotFoundException;
import com.appdomotica.mantenimiento.dto.MantenimientoDtos.*;
import com.appdomotica.mantenimiento.entity.EquipoInstalado;
import com.appdomotica.mantenimiento.entity.EstadoRecordatorio;
import com.appdomotica.mantenimiento.entity.MantenimientoRecordatorio;
import com.appdomotica.mantenimiento.repository.EquipoInstaladoRepository;
import com.appdomotica.mantenimiento.repository.MantenimientoRecordatorioRepository;
import com.appdomotica.usuarios.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Recordatorios de mantenimiento (RF-MAN-07..09): uno por proyecto, a 6 meses del cierre (RN-08).
 *
 * La tarea diaria inserta los que ya tocan; como revisa todos los proyectos cerrados, si el servidor
 * estuvo apagado unos días los recupera al siguiente intento. La restricción única de la base evita
 * duplicados aunque dos instancias corran a la vez.
 */
@Service
public class RecordatoriosService {
    public static final ZoneId CR = ZoneId.of("America/Costa_Rica");
    public static final int MESES = 6;
    static final int DIAS_PROXIMOS = 30;
    static final int DIAS_RECIENTES = 30;
    private static final long CANDADO = 0x4841555345544D4EL; // "HAUSETMN"
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-CR"));

    private final JdbcTemplate jdbc;
    private final MantenimientoRecordatorioRepository recordatorios;
    private final EquipoInstaladoRepository equipos;
    private final String empresa;

    public RecordatoriosService(JdbcTemplate jdbc, MantenimientoRecordatorioRepository recordatorios,
                                EquipoInstaladoRepository equipos,
                                @Value("${hauset.empresa.nombre-comercial:HAUSET}") String empresa) {
        this.jdbc = jdbc;
        this.recordatorios = recordatorios;
        this.equipos = equipos;
        this.empresa = empresa;
    }

    /** Crea los recordatorios que ya tocan. Devuelve cuántos creó (0 si otra instancia lo está haciendo). */
    @Transactional
    public int generar(LocalDate hoy) {
        Boolean libre = jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, CANDADO);
        if (!Boolean.TRUE.equals(libre)) {
            return 0;
        }
        return jdbc.update("""
                insert into mantenimiento_recordatorios (proyecto_id, fecha_programada)
                select p.id, (p.fecha_cierre + make_interval(months => ?))::date
                from proyectos p
                where p.estado = 'CERRADO' and p.fecha_cierre is not null
                  and (p.fecha_cierre + make_interval(months => ?))::date <= ?
                on conflict (proyecto_id, fecha_programada) do nothing
                """, MESES, MESES, hoy);
    }

    @Transactional(readOnly = true)
    public Recordatorios listar(LocalDate hoy) {
        List<Recordatorio> pendientes = dtos(recordatorios.conEstado(EstadoRecordatorio.PENDIENTE));
        List<Recordatorio> recientes = dtos(recordatorios.resueltosDesde(
                hoy.minusDays(DIAS_RECIENTES).atStartOfDay(CR).toOffsetDateTime()));
        List<Proximo> proximos = jdbc.query("""
                select p.id, c.nombre, p.fecha_cierre
                from proyectos p join cotizaciones q on q.id = p.cotizacion_id join clientes c on c.id = q.cliente_id
                where p.estado = 'CERRADO' and p.fecha_cierre is not null
                  and (p.fecha_cierre + make_interval(months => ?))::date between ? and ?
                  and not exists (select 1 from mantenimiento_recordatorios r where r.proyecto_id = p.id)
                order by p.fecha_cierre
                """, (rs, i) -> {
            LocalDate cierre = rs.getObject(3, LocalDate.class);
            return new Proximo(rs.getObject(1, UUID.class), rs.getString(2), cierre, cierre.plusMonths(MESES));
        }, MESES, hoy.plusDays(1), hoy.plusDays(DIAS_PROXIMOS));
        return new Recordatorios(pendientes, proximos, recientes);
    }

    /** Contactado u omitido (RF-MAN-09). Solo una vez: un recordatorio resuelto no se vuelve a tocar. */
    @Transactional
    public Recordatorio resolver(UUID id, EstadoRecordatorio nuevo, ResolverRecordatorioRequest req, Usuario usuario) {
        if (nuevo == EstadoRecordatorio.PENDIENTE) {
            throw new BadRequestException("Estado no válido");
        }
        MantenimientoRecordatorio r = recordatorios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado"));
        if (!req.version().equals(r.getVersion())) {
            throw new ConflictException("Alguien más actualizó el recordatorio. Recarga y vuelve a intentar.");
        }
        if (r.getEstado() != EstadoRecordatorio.PENDIENTE) {
            throw new ConflictException("Este recordatorio ya se marcó como "
                    + (r.getEstado() == EstadoRecordatorio.CONTACTADO ? "contactado" : "omitido"));
        }
        r.setEstado(nuevo);
        r.setContactadoPor(usuario);
        r.setFechaContacto(OffsetDateTime.now());
        r.setNota(req.nota() == null || req.nota().isBlank() ? null : req.nota().trim());
        r.setUpdatedAt(OffsetDateTime.now());
        recordatorios.saveAndFlush(r);
        return dtos(List.of(r)).get(0);
    }

    private List<Recordatorio> dtos(List<MantenimientoRecordatorio> lista) {
        List<Recordatorio> salida = new ArrayList<>();
        for (MantenimientoRecordatorio r : lista) {
            var p = r.getProyecto();
            var cliente = p.getCotizacion().getCliente();
            List<String> nombres = equipos.activosDe(p.getId()).stream()
                    .map(EquipoInstalado::getNombreReferencia).toList();
            salida.add(new Recordatorio(r.getId(), p.getId(), cliente.getNombre(), cliente.getTelefono(),
                    cliente.getDireccion(), p.getFechaCierre(), r.getFechaProgramada(), r.getEstado(),
                    r.getContactadoPor() == null ? null : r.getContactadoPor().getNombre(), r.getFechaContacto(),
                    r.getNota(), nombres, mensaje(cliente.getNombre(), p.getFechaCierre(), nombres), r.getVersion()));
        }
        return salida;
    }

    /** Mensaje prellenado para el cliente (RF-MAN-08). Lo envía una persona por WhatsApp. */
    String mensaje(String cliente, LocalDate instalacion, List<String> nombres) {
        String que;
        if (nombres.isEmpty()) {
            que = "su sistema";
        } else if (nombres.size() <= 3) {
            que = nombres.size() == 1 ? nombres.get(0)
                    : String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.get(nombres.size() - 1);
        } else {
            que = "sus equipos (" + String.join(", ", nombres.subList(0, 3)) + " y " + (nombres.size() - 3) + " más)";
        }
        String cuando = instalacion == null ? "Hace 6 meses" : "El " + FECHA.format(instalacion);
        return "Hola " + cliente + ", le saluda " + empresa + ". " + cuando + " instalamos " + que
                + " y ya es momento del mantenimiento preventivo, para que todo siga funcionando bien. "
                + "¿Le parece si coordinamos una visita?";
    }
}
