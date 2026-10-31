package com.appdomotica.proyectos.service;

import com.appdomotica.proyectos.dto.ProyectoDtos.Firma;
import com.appdomotica.proyectos.dto.ProyectoDtos.ProyectoDetalle;
import com.appdomotica.proyectos.dto.ProyectoDtos.Tarea;
import com.appdomotica.proyectos.dto.ProyectoDtos.UbicacionProyecto;
import com.appdomotica.proyectos.dto.ProyectoDtos.Visita;
import com.appdomotica.proyectos.entity.EstadoTarea;
import com.appdomotica.proyectos.entity.EstadoVisita;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reporte final de entrega (RF-PRY-15). Se arma solo con lo registrado en el checklist (tareas, notas,
 * fotos de antes y después, visitas y firma), sin captura duplicada. No lleva precios.
 */
@Service
public class ReporteEntregaPdfService {

    private static final Color MORADO = new Color(0x7C, 0x3A, 0xED);
    private static final Color MORADO_OSCURO = new Color(0x4C, 0x1D, 0x95);
    private static final Color LILA = new Color(0xDD, 0xD0, 0xFB);
    private static final Color FONDO = new Color(0xF7, 0xF3, 0xFF);
    private static final Color GRIS = new Color(0x47, 0x55, 0x69);
    private static final Color VERDE = new Color(0x15, 0x80, 0x3D);
    private static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", Locale.US);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, MORADO_OSCURO);
    private static final Font EMPRESA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, MORADO_OSCURO);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS);
    private static final Font PEQUENA_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, MORADO_OSCURO);
    private static final Font SUBSECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, MORADO);
    private static final Font TAREA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK);
    private static final Font HECHA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, VERDE);
    private static final Font NO_APLICA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MORADO);

    private static final float ALTO_ENCABEZADO = 78;
    private static final int FOTOS_POR_MOMENTO = 2;
    private static final int LADO_MAXIMO_FOTO = 900;

    private final String nombreComercial;
    private final String razonSocial;
    private final String cedulaJuridica;
    private final String telefono;
    private final String correo;
    private final byte[] logo;

    public ReporteEntregaPdfService(
            @Value("${hauset.empresa.nombre-comercial:HAUSET}") String nombreComercial,
            @Value("${hauset.empresa.razon-social:}") String razonSocial,
            @Value("${hauset.empresa.cedula-juridica:}") String cedulaJuridica,
            @Value("${hauset.empresa.telefono:}") String telefono,
            @Value("${hauset.empresa.correo:}") String correo) {
        this.nombreComercial = nombreComercial;
        this.razonSocial = razonSocial;
        this.cedulaJuridica = cedulaJuridica;
        this.telefono = telefono;
        this.correo = correo;
        byte[] l;
        try (InputStream in = new ClassPathResource("branding/logo.png").getInputStream()) {
            l = in.readAllBytes();
        } catch (IOException e) {
            l = null;
        }
        this.logo = l;
    }

    /**
     * @param archivo  lee el contenido de una foto o de la firma por su id (null si no está)
     * @param preliminar el proyecto todavía no se cierra: marca de agua "VISTA PREVIA"
     */
    public byte[] generar(ProyectoDetalle p, Function<UUID, byte[]> archivo, boolean preliminar) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 36, 36, 30 + ALTO_ENCABEZADO, 60);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Pagina(preliminar));
        doc.addTitle("Reporte de entrega - " + p.clienteNombre());
        doc.addAuthor(nombreComercial);
        doc.open();

        doc.add(datosProyecto(p));
        doc.add(resumen(p));
        visitas(doc, p);
        trabajo(doc, p, archivo);
        conformidad(doc, p.firma(), archivo);

        doc.close();
        return out.toByteArray();
    }

    // ---------- secciones ----------

    private PdfPTable datosProyecto(ProyectoDetalle p) {
        PdfPTable t = new PdfPTable(new float[]{1.3f, 1});
        t.setWidthPercentage(100);
        PdfPCell izq = celdaSinBorde();
        izq.addElement(dato("Cliente: ", p.clienteNombre()));
        izq.addElement(dato("Teléfono: ", texto(p.clienteTelefono())));
        izq.addElement(dato("Dirección: ", texto(p.clienteDireccion())));
        t.addCell(izq);
        PdfPCell der = celdaSinBorde();
        der.addElement(dato("Cotización: ", texto(p.cotizacionNumero())));
        if (p.instrucciones() != null) {
            der.addElement(dato("Instrucciones: ", "versión " + p.instrucciones().numero()));
        }
        der.addElement(dato("Fecha de entrega: ", p.firma() != null ? fecha(p.firma().fecha()) : "sin cerrar"));
        t.addCell(der);
        t.setSpacingAfter(4);
        return t;
    }

    private PdfPTable resumen(ProyectoDetalle p) {
        long hechas = p.tareas().stream().filter(x -> x.estado() == EstadoTarea.COMPLETADA).count();
        long noAplica = p.tareas().stream().filter(x -> x.estado() == EstadoTarea.NO_APLICA).count();
        long pendientes = p.tareas().size() - hechas - noAplica;
        long visitas = p.visitas().stream().filter(v -> v.iniciadaEn() != null).count();
        StringBuilder s = new StringBuilder();
        s.append(p.tareas().size()).append(p.tareas().size() == 1 ? " tarea: " : " tareas: ")
                .append(hechas).append(hechas == 1 ? " completada" : " completadas");
        if (noAplica > 0) {
            s.append(", ").append(noAplica).append(" no aplica");
        }
        if (pendientes > 0) {
            s.append(", ").append(pendientes).append(pendientes == 1 ? " pendiente" : " pendientes");
        }
        s.append(" · ").append(visitas).append(visitas == 1 ? " visita" : " visitas");
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(s.toString(), NEGRITA));
        c.setBackgroundColor(FONDO);
        c.setBorderColor(LILA);
        c.setPadding(7);
        t.addCell(c);
        t.setSpacingAfter(10);
        return t;
    }

    private void visitas(Document doc, ProyectoDetalle p) {
        List<Visita> hechas = p.visitas().stream()
                .filter(v -> v.estado() != EstadoVisita.CANCELADA && v.iniciadaEn() != null)
                .toList();
        if (hechas.isEmpty()) {
            return;
        }
        doc.add(seccion("Visitas"));
        PdfPTable t = new PdfPTable(new float[]{0.4f, 1.3f, 1.5f, 1.1f, 0.7f, 2.4f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : new String[]{"N°", "Fecha", "Responsable", "Resultado", "Tareas", "Observación"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, CABECERA));
            c.setBackgroundColor(MORADO);
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(5);
            t.addCell(c);
        }
        int n = 0;
        for (Visita v : hechas) {
            Color fondo = n++ % 2 == 1 ? FONDO : Color.WHITE;
            t.addCell(celda(String.valueOf(v.numero()), fondo));
            t.addCell(celda(fechaHora(v.iniciadaEn()), fondo));
            t.addCell(celda(texto(v.iniciadaPor() != null ? v.iniciadaPor() : v.asignadoA()), fondo));
            t.addCell(celda(switch (v.estado()) {
                case EN_CURSO -> "En curso";
                case REQUIERE_RETORNO -> "Requiere retorno";
                default -> "Terminada";
            }, fondo));
            t.addCell(celda(String.valueOf(v.tareasHechas()), fondo));
            t.addCell(celda(v.motivoRetorno() != null ? "Hay que volver: " + v.motivoRetorno()
                    : v.motivo() != null ? "Motivo: " + v.motivo() : "—", fondo));
        }
        t.setSpacingAfter(10);
        doc.add(t);
    }

    private void trabajo(Document doc, ProyectoDetalle p, Function<UUID, byte[]> archivo) {
        doc.add(seccion("Trabajo realizado"));
        for (UbicacionProyecto u : p.ubicaciones()) {
            List<Tarea> deAqui = p.tareas().stream().filter(t -> u.id().equals(t.ubicacionId())).toList();
            if (!deAqui.isEmpty()) {
                grupo(doc, "Ubicación " + u.numero() + (u.nota() != null && !u.nota().isBlank() ? ": " + u.nota() : ""),
                        deAqui, archivo);
            }
        }
        List<Tarea> generales = p.tareas().stream().filter(t -> t.ubicacionId() == null).toList();
        if (!generales.isEmpty()) {
            grupo(doc, "Tareas generales", generales, archivo);
        }
    }

    private void grupo(Document doc, String titulo, List<Tarea> tareas, Function<UUID, byte[]> archivo) {
        Paragraph h = new Paragraph(titulo, SUBSECCION);
        h.setSpacingBefore(6);
        h.setSpacingAfter(4);
        doc.add(h);
        for (Tarea t : tareas) {
            doc.add(tarea(t, archivo));
        }
    }

    private PdfPTable tarea(Tarea t, Function<UUID, byte[]> archivo) {
        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.setKeepTogether(true);
        caja.setSpacingAfter(6);

        PdfPCell c = new PdfPCell();
        c.setBorderColor(LILA);
        c.setPadding(7);
        Paragraph titulo = new Paragraph();
        titulo.add(new Chunk(t.descripcion(), TAREA));
        titulo.add(new Chunk("   " + switch (t.estado()) {
            case COMPLETADA -> "COMPLETADA";
            case NO_APLICA -> "NO APLICA";
            case PENDIENTE -> "PENDIENTE";
        }, t.estado() == EstadoTarea.NO_APLICA ? NO_APLICA : t.estado() == EstadoTarea.COMPLETADA ? HECHA : PEQUENA));
        c.addElement(titulo);
        if (t.completadaPor() != null) {
            Paragraph quien = new Paragraph(t.completadaPor() + " · " + fechaHora(t.completadaEn())
                    + (t.visitaNumero() != null ? " · visita " + t.visitaNumero() : ""), PEQUENA);
            c.addElement(quien);
        }
        if (t.notas() != null && !t.notas().isBlank()) {
            Paragraph nota = new Paragraph();
            nota.add(new Chunk("Nota: ", PEQUENA_NEGRITA));
            nota.add(new Chunk(t.notas(), NORMAL));
            nota.setSpacingBefore(2);
            c.addElement(nota);
        }
        if (!t.fotosAntes().isEmpty() || !t.fotosDespues().isEmpty()) {
            PdfPTable fotos = new PdfPTable(2);
            fotos.setWidthPercentage(100);
            fotos.setSpacingBefore(5);
            fotos.addCell(momento("Antes", t.fotosAntes(), archivo));
            fotos.addCell(momento("Después", t.fotosDespues(), archivo));
            c.addElement(fotos);
        }
        caja.addCell(c);
        return caja;
    }

    private PdfPCell momento(String titulo, List<UUID> ids, Function<UUID, byte[]> archivo) {
        PdfPCell celda = celdaSinBorde();
        celda.setPaddingRight(6);
        celda.addElement(new Paragraph(titulo, PEQUENA_NEGRITA));
        PdfPTable fila = new PdfPTable(FOTOS_POR_MOMENTO);
        fila.setWidthPercentage(100);
        fila.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        for (UUID id : ids.stream().limit(FOTOS_POR_MOMENTO).toList()) {
            Image img = imagen(archivo.apply(id));
            PdfPCell f = celdaSinBorde();
            f.setPadding(2);
            if (img != null) {
                img.scaleToFit(118, 100);
                f.addElement(img);
            } else {
                f.addElement(new Paragraph("(foto no disponible)", PEQUENA));
            }
            fila.addCell(f);
        }
        fila.completeRow();
        if (ids.isEmpty()) {
            celda.addElement(new Paragraph("Sin fotos", PEQUENA));
        } else {
            celda.addElement(fila);
        }
        if (ids.size() > FOTOS_POR_MOMENTO) {
            celda.addElement(new Paragraph("+" + (ids.size() - FOTOS_POR_MOMENTO) + " más en el sistema", PEQUENA));
        }
        return celda;
    }

    private void conformidad(Document doc, Firma firma, Function<UUID, byte[]> archivo) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        t.setSpacingBefore(14);
        PdfPCell c = new PdfPCell();
        c.setBorderColor(MORADO);
        c.setBorderWidth(1);
        c.setPadding(10);
        c.addElement(new Paragraph("Conformidad del cliente", SECCION));
        Paragraph decl = new Paragraph("Recibo a conformidad la instalación descrita en este reporte.", NORMAL);
        decl.setSpacingBefore(4);
        c.addElement(decl);
        if (firma == null) {
            Paragraph sin = new Paragraph("Pendiente de firma: el proyecto todavía no se cierra.", NEGRITA);
            sin.setSpacingBefore(30);
            c.addElement(sin);
        } else {
            byte[] bytes = firma.archivoId() != null ? archivo.apply(firma.archivoId()) : null;
            Image img = null;
            try {
                img = bytes != null ? Image.getInstance(bytes) : null;
            } catch (Exception ignorado) {
                // Si la imagen no se puede leer, el reporte sigue con el nombre y la fecha.
            }
            if (img != null) {
                img.scaleToFit(230, 95);
                c.addElement(img);
            }
            PdfPTable linea = new PdfPTable(new float[]{1, 1.4f});
            linea.setWidthPercentage(100);
            PdfPCell l = new PdfPCell();
            l.setBorder(Rectangle.TOP);
            l.setBorderColor(GRIS);
            l.addElement(new Paragraph(firma.nombre(), NEGRITA));
            l.addElement(new Paragraph(fechaHora(firma.fecha()), PEQUENA));
            linea.addCell(l);
            linea.addCell(celdaSinBorde());
            c.addElement(linea);
            if (firma.registradaPor() != null) {
                c.addElement(new Paragraph("Firma tomada en sitio por " + firma.registradaPor()
                        + " (visita " + firma.visitaNumero() + ").", PEQUENA));
            }
        }
        t.addCell(c);
        doc.add(t);
    }

    // ---------- apoyo ----------

    /** Las fotos de campo pesan varios MB: se reducen a 900 px y JPEG para que el PDF se pueda compartir. */
    static Image imagen(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                return null;
            }
            original = enderezar(original, orientacionExif(bytes));
            double escala = Math.min(1.0, (double) LADO_MAXIMO_FOTO / Math.max(original.getWidth(), original.getHeight()));
            int w = Math.max(1, (int) Math.round(original.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(original.getHeight() * escala));
            BufferedImage chica = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = chica.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(original, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);
            try (MemoryCacheImageOutputStream salida = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(salida);
                writer.write(null, new IIOImage(chica, null, null), param);
            } finally {
                writer.dispose();
            }
            return Image.getInstance(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Las cámaras de los teléfonos guardan la foto acostada y anotan en el EXIF cómo girarla; ImageIO no lo
     * aplica. Devuelve la orientación (1 = derecha) leyendo solo la etiqueta 0x0112 del bloque APP1.
     */
    static int orientacionExif(byte[] d) {
        try {
            if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int i = 2;
            while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
                int marcador = d[i + 1] & 0xFF;
                int largo = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
                if (marcador == 0xE1 && i + 10 <= d.length && new String(d, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("Exif")) {
                    int t = i + 10;
                    boolean le = d[t] == 'I';
                    int ifd = t + leer(d, t + 4, 4, le);
                    int n = leer(d, ifd, 2, le);
                    for (int k = 0; k < n; k++) {
                        int e = ifd + 2 + 12 * k;
                        if (leer(d, e, 2, le) == 0x0112) {
                            return leer(d, e + 8, 2, le);
                        }
                    }
                    return 1;
                }
                if (marcador == 0xDA) {
                    return 1; // Empezó la imagen: no hay EXIF.
                }
                i += 2 + largo;
            }
        } catch (RuntimeException e) {
            // EXIF dañado: se deja como viene.
        }
        return 1;
    }

    private static int leer(byte[] d, int pos, int n, boolean le) {
        int v = 0;
        for (int k = 0; k < n; k++) {
            int b = d[pos + (le ? n - 1 - k : k)] & 0xFF;
            v = (v << 8) | b;
        }
        return v;
    }

    /** Gira según la orientación EXIF (3 = 180°, 6 = 90° a la derecha, 8 = 90° a la izquierda). */
    static BufferedImage enderezar(BufferedImage img, int orientacion) {
        int grados = switch (orientacion) {
            case 3, 4 -> 180;
            case 5, 6 -> 90;
            case 7, 8 -> 270;
            default -> 0;
        };
        if (grados == 0) {
            return img;
        }
        int w = img.getWidth(), h = img.getHeight();
        boolean cruza = grados != 180;
        BufferedImage out = new BufferedImage(cruza ? h : w, cruza ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
        g.rotate(Math.toRadians(grados));
        g.translate(-w / 2.0, -h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, SECCION);
        p.setSpacingBefore(4);
        p.setSpacingAfter(5);
        return p;
    }

    private static Paragraph dato(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.setLeading(13);
        p.add(new Chunk(etiqueta, NEGRITA));
        p.add(new Chunk(valor, NORMAL));
        return p;
    }

    private static PdfPCell celda(String texto, Color fondo) {
        PdfPCell c = new PdfPCell(new Phrase(texto, NORMAL));
        c.setPadding(5);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(fondo);
        return c;
    }

    private static PdfPCell celdaSinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String fecha(OffsetDateTime t) {
        return t == null ? "—" : t.atZoneSameInstant(ZONA).format(FECHA);
    }

    private static String fechaHora(OffsetDateTime t) {
        return t == null ? "—" : t.atZoneSameInstant(ZONA).format(FECHA_HORA).toLowerCase(Locale.ROOT);
    }

    // ---------- encabezado y pie ----------

    private final class Pagina extends PdfPageEventHelper {
        private final boolean preliminar;
        private PdfTemplate totalPaginas;

        Pagina(boolean preliminar) {
            this.preliminar = preliminar;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPaginas = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle pagina = document.getPageSize();
            float izq = document.left(), der = document.right();

            PdfPTable enc = new PdfPTable(new float[]{1, 1.25f, 1.75f});
            enc.setTotalWidth(der - izq);
            enc.setLockedWidth(true);
            PdfPCell logoCelda = celdaSinBorde();
            logoCelda.setFixedHeight(62);
            logoCelda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                if (logo != null) {
                    Image img = Image.getInstance(logo);
                    img.scaleToFit(110, 55);
                    logoCelda.addElement(img);
                } else {
                    logoCelda.addElement(new Paragraph(nombreComercial, EMPRESA));
                }
            } catch (Exception e) {
                logoCelda.addElement(new Paragraph(nombreComercial, EMPRESA));
            }
            enc.addCell(logoCelda);
            PdfPCell titulo = celdaSinBorde();
            titulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph tp = new Paragraph("Reporte de entrega", TITULO);
            tp.setAlignment(Element.ALIGN_CENTER);
            titulo.addElement(tp);
            enc.addCell(titulo);
            PdfPCell empresa = celdaSinBorde();
            empresa.setVerticalAlignment(Element.ALIGN_MIDDLE);
            for (Paragraph linea : List.of(new Paragraph(razonSocial, EMPRESA),
                    new Paragraph("Céd. jurídica " + cedulaJuridica, PEQUENA),
                    new Paragraph(telefono + " · " + correo, PEQUENA))) {
                linea.setAlignment(Element.ALIGN_RIGHT);
                empresa.addElement(linea);
            }
            enc.addCell(empresa);
            enc.writeSelectedRows(0, -1, izq, pagina.getHeight() - 24, cb);

            cb.saveState();
            cb.setColorFill(LILA);
            cb.rectangle(izq, pagina.getHeight() - 24 - ALTO_ENCABEZADO + 6, der - izq, 2.5f);
            cb.fill();
            cb.rectangle(izq, document.bottom() - 14, der - izq, 1.5f);
            cb.fill();
            cb.restoreState();

            float y = document.bottom() - 28;
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(
                    nombreComercial + " · " + razonSocial + " · Céd. jurídica " + cedulaJuridica, PEQUENA),
                    (izq + der) / 2, y, 0);
            String texto = "Página " + writer.getPageNumber() + " de ";
            float x = der - 18 - PEQUENA_NEGRITA.getBaseFont().getWidthPoint(texto, PEQUENA_NEGRITA.getSize());
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(texto, PEQUENA_NEGRITA), x, y - 12, 0);
            cb.addTemplate(totalPaginas, der - 18, y - 12);

            if (preliminar) {
                ColumnText.showTextAligned(writer.getDirectContentUnder(), Element.ALIGN_CENTER,
                        new Phrase("VISTA PREVIA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 80,
                                new Color(0xEE, 0xE6, 0xFF))),
                        pagina.getWidth() / 2, pagina.getHeight() / 2, 45);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(totalPaginas, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber() - 1), PEQUENA_NEGRITA), 0, 0, 0);
        }
    }
}
