package com.appdomotica.proyectos.service;

import com.appdomotica.common.exception.BadRequestException;
import com.appdomotica.common.exception.ConflictException;
import com.appdomotica.mantenimiento.repository.EquipoInstaladoRepository;
import com.appdomotica.mantenimiento.service.RecordatoriosService;
import com.appdomotica.multimedia.entity.ArchivoAdjunto;
import com.appdomotica.multimedia.repository.ArchivoAdjuntoRepository;
import com.appdomotica.multimedia.service.ArchivoAdjuntoService;
import com.appdomotica.multimedia.service.ValidadorImagen;
import com.appdomotica.proyectos.dto.ProyectoDtos.ProyectoDetalle;
import com.appdomotica.proyectos.entity.*;
import com.appdomotica.proyectos.repository.ChecklistTareaRepository;
import com.appdomotica.proyectos.repository.FirmaConformidadRepository;
import com.appdomotica.proyectos.repository.ProyectoEventoRepository;
import com.appdomotica.proyectos.repository.ReporteEntregaRepository;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cierre del proyecto (RN-07, RF-PRY-13) y reporte de entrega (RF-PRY-15).
 *
 * Se cierra solo si todas las tareas vigentes están completadas o "no aplica", las ubicaciones con checklist
 * están completadas (todos los puntos marcados y fotos de antes y después), no hay cambios de
 * instrucción ni extras sin confirmar, y el cliente firma. En la misma transacción: firma ligada a la
 * visita en curso, visita terminada, visitas por venir canceladas, proyecto cerrado, fecha de instalación de
 * los equipos y PDF guardado.
 */
@Service
@RequiredArgsConstructor
public class CierreProyectoService {
    public static final String ENTIDAD_FIRMA = "FIRMA_CONFORMIDAD";
    public static final String CATEGORIA_FIRMA = "FIRMA_CLIENTE";
    public static final String ENTIDAD_REPORTE = "REPORTE_ENTREGA";
    public static final String CATEGORIA_REPORTE = "DOCUMENTO_PDF";
    private static final long MAX_FIRMA = 2L * 1024 * 1024;

    private final ProyectoService proyectos;
    private final VisitaService visitas;
    private final ChecklistTareaRepository tareas;
    private final FirmaConformidadRepository firmas;
    private final ReporteEntregaRepository reportes;
    private final ProyectoEventoRepository eventos;
    private final ArchivoAdjuntoService archivos;
    private final ArchivoAdjuntoRepository archivosRepo;
    private final ReporteEntregaPdfService pdf;
    private final EquipoInstaladoRepository equipos;

    @Transactional
    public ProyectoDetalle cerrar(UUID proyectoId, MultipartFile firma, String nombre, OffsetDateTime ocurridoEn,
                                  Usuario usuario) {
        Proyecto p = proyectos.abierto(proyectoId);
        String quien = nombre == null ? "" : nombre.trim();
        if (quien.isEmpty()) {
            throw new BadRequestException("Escribe el nombre de quien recibe y firma");
        }
        if (quien.length() > 150) {
            throw new BadRequestException("El nombre es muy largo (máximo 150 caracteres)");
        }
        ValidadorImagen.validar(firma);
        if (firma.getSize() > MAX_FIRMA) {
            throw new BadRequestException("La imagen de la firma supera 2 MB");
        }
        List<String> faltan = new ArrayList<>();
        long pendientes = tareas.vigentesDelProyecto(p.getId()).stream()
                .filter(t -> t.getEstado() == EstadoTarea.PENDIENTE).count();
        if (pendientes > 0) {
            faltan.add(pendientes == 1 ? "Falta 1 tarea por completar" : "Faltan " + pendientes + " tareas por completar");
        }
        // Ubicaciones con checklist: todos los puntos marcados y fotos de antes y después (se calcula).
        faltan.addAll(proyectos.faltaEnUbicaciones(p));
        faltan.addAll(proyectos.bloqueosCierre(p));
        if (!faltan.isEmpty()) {
            throw new ConflictException("No se puede cerrar: " + String.join("; ", faltan).toLowerCase() + ".");
        }

        OffsetDateTime cuando = VisitaService.momento(ocurridoEn);
        Visita visita = visitas.iniciar(p, usuario, cuando);
        UUID archivoFirma = archivos.subirArchivo(firma, ENTIDAD_FIRMA, p.getId(), CATEGORIA_FIRMA,
                "Firma de " + quien, usuario).getId();
        firmas.save(FirmaConformidad.builder()
                .visita(visita)
                .firmadoPorCliente(quien)
                .archivoId(archivoFirma)
                .registradaPor(usuario)
                .fecha(cuando)
                .build());
        visitas.cerrarVisita(visita, EstadoVisita.COMPLETADA, usuario, cuando);
        visitas.cancelarPorVenir(p);
        String antes = VisitaService.etiqueta(p.getEstado());
        p.setEstado(EstadoProyecto.CERRADO);
        // El día del cierre es la fecha de instalación de los equipos y la base del recordatorio (RF-MAN-02, RN-08).
        p.setFechaCierre(cuando.atZoneSameInstant(RecordatoriosService.CR).toLocalDate());
        p.setUpdatedAt(OffsetDateTime.now());
        equipos.fijarFechaInstalacion(p.getId(), p.getFechaCierre());
        eventos.save(new ProyectoEvento(p, "PROYECTO_CERRADO", usuario, "Recibido a conformidad por " + quien));
        eventos.save(new ProyectoEvento(p, "ESTADO", usuario, antes + " → Cerrado"));
        tareas.flush();

        // El reporte se genera una vez, con lo que quedó registrado, y se guarda tal cual se entregó.
        byte[] bytes = pdf.generar(proyectos.detalle(p), this::contenido, false);
        ArchivoAdjunto guardado = archivos.guardarGenerado(bytes, nombreReporte(p), "application/pdf",
                ENTIDAD_REPORTE, p.getId(), CATEGORIA_REPORTE, usuario);
        reportes.save(ReporteEntrega.builder().proyecto(p).generadoPor(usuario).archivoId(guardado.getId()).build());
        return proyectos.detalle(p);
    }

    /** El reporte guardado al cerrar; si el proyecto sigue abierto, una vista previa con marca de agua. */
    @Transactional(readOnly = true)
    public Reporte reporte(UUID proyectoId) {
        Proyecto p = proyectos.buscarProyecto(proyectoId);
        var guardado = reportes.findByProyectoId(p.getId());
        if (guardado.isPresent() && guardado.get().getArchivoId() != null) {
            return new Reporte(contenido(guardado.get().getArchivoId()), nombreReporte(p));
        }
        return new Reporte(pdf.generar(proyectos.detalle(p), this::contenido, true),
                "vista-previa-" + nombreReporte(p));
    }

    public record Reporte(byte[] pdf, String nombre) {
    }

    private byte[] contenido(UUID archivoId) {
        // Directo al repositorio: un archivo que falta no debe marcar la transacción para deshacerse.
        ArchivoAdjunto a = archivosRepo.findById(archivoId).filter(x -> x.getDeletedAt() == null).orElse(null);
        if (a == null) {
            return null;
        }
        try (InputStream in = archivos.cargarContenido(a).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String nombreReporte(Proyecto p) {
        String cliente = p.getCotizacion().getCliente().getNombre()
                .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("(^-|-$)", "");
        return "reporte-entrega-" + cliente + ".pdf";
    }
}
