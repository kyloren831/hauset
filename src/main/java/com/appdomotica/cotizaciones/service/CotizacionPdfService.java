package com.appdomotica.cotizaciones.service;

import com.appdomotica.cotizaciones.entity.Cotizacion;
import com.appdomotica.cotizaciones.entity.CotizacionItem;
import com.appdomotica.cotizaciones.entity.EstadoCotizacion;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PDF de la cotización para el cliente (RF-COT-08), con la estructura de la proforma que ya usa la
 * empresa (encabezado con datos de la sociedad, receptor, líneas de detalle y totales con el monto en
 * letras) en los colores de Hauset. Muestra precio de venta e IVA; nunca el neto, el proveedor ni el margen.
 */
@Service
public class CotizacionPdfService {

    private static final Color MORADO = new Color(0x7C, 0x3A, 0xED);
    private static final Color MORADO_OSCURO = new Color(0x4C, 0x1D, 0x95);
    private static final Color LILA = new Color(0xDD, 0xD0, 0xFB);
    private static final Color FONDO = new Color(0xF7, 0xF3, 0xFF);
    private static final Color GRIS = new Color(0x47, 0x55, 0x69);
    private static final ZoneId ZONA = ZoneId.of("America/Costa_Rica");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy  hh:mm a", Locale.US);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA, 14, Color.BLACK);
    private static final Font EMPRESA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, MORADO_OSCURO);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS);
    private static final Font PEQUENA_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, MORADO_OSCURO);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, MORADO_OSCURO);

    /** Espacio que ocupa el encabezado repetido en cada página. */
    private static final float ALTO_ENCABEZADO = 140;

    private final String nombreComercial;
    private final String razonSocial;
    private final String cedulaJuridica;
    private final String telefono;
    private final String correo;
    private final String direccion;
    private final byte[] logo;

    public CotizacionPdfService(
            @Value("${hauset.empresa.nombre-comercial:HAUSET}") String nombreComercial,
            @Value("${hauset.empresa.razon-social:}") String razonSocial,
            @Value("${hauset.empresa.cedula-juridica:}") String cedulaJuridica,
            @Value("${hauset.empresa.telefono:}") String telefono,
            @Value("${hauset.empresa.correo:}") String correo,
            @Value("${hauset.empresa.direccion:}") String direccion) {
        this.nombreComercial = nombreComercial;
        this.razonSocial = razonSocial;
        this.cedulaJuridica = cedulaJuridica;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
        this.logo = cargarLogo();
    }

    private static byte[] cargarLogo() {
        try (InputStream in = new ClassPathResource("branding/logo.png").getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public byte[] generar(Cotizacion c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 30, 30, 30 + ALTO_ENCABEZADO, 95);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        boolean borrador = c.getEstado() == EstadoCotizacion.BORRADOR;
        writer.setPageEvent(new Pagina(c, borrador));
        doc.addTitle("Cotización " + (c.getNumero() != null ? c.getNumero() : "borrador"));
        doc.addAuthor(nombreComercial);
        doc.open();

        doc.add(receptor(c));
        doc.add(lineaMorada(3, 6, 0));
        Paragraph seccion = new Paragraph("Líneas de Detalle", SECCION);
        seccion.setAlignment(Element.ALIGN_CENTER);
        seccion.setSpacingAfter(4);
        doc.add(seccion);
        doc.add(detalle(c));
        doc.add(lineaMorada(1.5f, 2, 8));
        doc.add(notasYTotales(c));

        Paragraph letras = new Paragraph(MontoEnLetras.colones(c.getTotalConIva()), NORMAL);
        letras.setAlignment(Element.ALIGN_RIGHT);
        letras.setSpacingBefore(12);
        doc.add(letras);

        doc.close();
        return out.toByteArray();
    }

    // ---------- cuerpo ----------

    private PdfPTable receptor(Cotizacion c) {
        var cliente = c.getCliente();
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        PdfPCell izq = celdaSinBorde();
        izq.addElement(dato("Receptor: ", cliente.getNombre()));
        izq.addElement(dato("Teléfono: ", cliente.getTelefono() != null ? cliente.getTelefono() : ""));
        izq.addElement(dato("Dirección: ", cliente.getDireccion() != null ? cliente.getDireccion() : ""));
        t.addCell(izq);
        PdfPCell der = celdaSinBorde();
        der.addElement(dato("Moneda: ", "Colones costarricenses (CRC)"));
        der.addElement(dato("Validez de la oferta: ", c.getValidezDias() + " días"));
        OffsetDateTime emision = emision(c);
        der.addElement(dato("Válida hasta: ", emision.plusDays(c.getValidezDias()).atZoneSameInstant(ZONA).format(FECHA)));
        t.addCell(der);
        return t;
    }

    private PdfPTable detalle(Cotizacion c) {
        PdfPTable t = new PdfPTable(new float[]{0.45f, 0.8f, 0.9f, 3.6f, 1.45f, 1.45f, 1.2f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        String[] titulos = {"#", "Cantidad", "Unidad\nMedida", "Descripción del\nProducto/Servicio", "Precio Unitario",
                "SubTotal", "Monto\nImpuestos"};
        for (String h : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(h, CABECERA));
            celda.setBackgroundColor(MORADO);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(5);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(celda);
        }
        int n = 1;
        for (CotizacionItem i : c.getItems()) {
            Color fondo = n % 2 == 0 ? FONDO : Color.WHITE;
            t.addCell(celda(String.valueOf(n++), Element.ALIGN_CENTER, fondo));
            t.addCell(celda(cantidad(i.getCantidad()), Element.ALIGN_RIGHT, fondo));
            t.addCell(celda("Unidad", Element.ALIGN_CENTER, fondo));
            t.addCell(celda(i.getProductoNombre(), Element.ALIGN_LEFT, fondo));
            t.addCell(celda(monto(i.getPrecioVenta()), Element.ALIGN_RIGHT, fondo));
            t.addCell(celda(monto(i.getSubtotal()), Element.ALIGN_RIGHT, fondo));
            t.addCell(celda(monto(i.getMontoIva()), Element.ALIGN_RIGHT, fondo));
        }
        return t;
    }

    private PdfPTable notasYTotales(Cotizacion c) {
        PdfPTable t = new PdfPTable(new float[]{1.25f, 1});
        t.setWidthPercentage(100);
        t.setKeepTogether(true);

        PdfPCell notas = celdaSinBorde();
        Paragraph p = new Paragraph();
        p.add(new Chunk("Notas: ", NEGRITA));
        if (c.getNotas() != null && !c.getNotas().isBlank()) {
            p.add(new Chunk(c.getNotas(), NORMAL));
        }
        notas.addElement(p);
        t.addCell(notas);

        PdfPTable caja = new PdfPTable(new float[]{2.4f, 0.35f, 1.8f});
        caja.setWidthPercentage(100);
        String iva = c.getIvaPorcentaje().stripTrailingZeros().toPlainString();
        filaTotal(caja, "Subtotal Neto:", monto(c.getTotalSinIva()), NEGRITA, false);
        filaTotal(caja, "Total IVA (" + iva + " %):", monto(c.getMontoIva()), NEGRITA, false);
        filaTotal(caja, "Total Cotización:", monto(c.getTotalConIva()), TOTAL, true);
        PdfPCell contenedor = new PdfPCell(caja);
        contenedor.setBorder(Rectangle.BOX);
        contenedor.setBorderColor(MORADO);
        contenedor.setBorderWidth(1);
        contenedor.setPadding(0);
        t.addCell(contenedor);
        return t;
    }

    private void filaTotal(PdfPTable t, String etiqueta, String valor, Font fuente, boolean resaltada) {
        Color fondo = resaltada ? LILA : Color.WHITE;
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, fuente));
        PdfPCell signo = new PdfPCell(new Phrase("¢", fuente));
        PdfPCell b = new PdfPCell(new Phrase(valor, fuente));
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        signo.setHorizontalAlignment(Element.ALIGN_CENTER);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell x : new PdfPCell[]{a, signo, b}) {
            x.setBorder(Rectangle.BOTTOM);
            x.setBorderColor(LILA);
            x.setBackgroundColor(fondo);
            x.setPadding(5);
        }
        signo.setBorder(Rectangle.BOTTOM | Rectangle.LEFT | Rectangle.RIGHT);
        t.addCell(a);
        t.addCell(signo);
        t.addCell(b);
    }

    private static Paragraph dato(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.setLeading(14);
        p.add(new Chunk(etiqueta, NEGRITA));
        p.add(new Chunk(valor, NORMAL));
        return p;
    }

    private static PdfPCell celda(String texto, int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, NORMAL));
        celda.setPadding(5);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setBackgroundColor(fondo);
        celda.setHorizontalAlignment(alineacion);
        return celda;
    }

    private static PdfPCell celdaSinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    /** Línea horizontal morada, como los separadores de la proforma. */
    private static PdfPTable lineaMorada(float grosor, float antes, float despues) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(antes);
        t.setSpacingAfter(despues);
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(grosor);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(LILA);
        t.addCell(c);
        return t;
    }

    private static OffsetDateTime emision(Cotizacion c) {
        return c.getFechaEnvio() != null ? c.getFechaEnvio() : c.getFecha();
    }

    /** 112096 → "112,096.00", como en la proforma (Helvetica no trae ₡; se usa ¢). */
    static String monto(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(valor);
    }

    private static String cantidad(int n) {
        return n + ".00";
    }

    // ---------- encabezado y pie en cada página ----------

    private final class Pagina extends PdfPageEventHelper {
        private final Cotizacion c;
        private final boolean borrador;
        private PdfTemplate totalPaginas;

        Pagina(Cotizacion c, boolean borrador) {
            this.c = c;
            this.borrador = borrador;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPaginas = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle pagina = document.getPageSize();
            float izq = document.left(), der = document.right(), ancho = der - izq;

            // Marco de la página.
            cb.saveState();
            cb.setColorStroke(MORADO_OSCURO);
            cb.setLineWidth(0.8f);
            cb.rectangle(18, 18, pagina.getWidth() - 36, pagina.getHeight() - 36);
            cb.stroke();
            cb.restoreState();

            encabezado().writeSelectedRows(0, -1, izq, pagina.getHeight() - 26, cb);

            // Pie.
            float y = document.bottom() - 18;
            barra(cb, izq, y + 10, ancho, 2.5f);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(
                    "Esta cotización no es un comprobante electrónico ni sustituye la factura.", PEQUENA_NEGRITA),
                    (izq + der) / 2, y - 4, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(
                    "Precios en colones costarricenses, IVA incluido en el total. Oferta válida por "
                            + c.getValidezDias() + " días desde su emisión.", PEQUENA),
                    (izq + der) / 2, y - 16, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(
                    nombreComercial + " · " + razonSocial + " · Céd. jurídica " + cedulaJuridica, PEQUENA),
                    (izq + der) / 2, y - 28, 0);
            String texto = "Página " + writer.getPageNumber() + " de ";
            Font f = PEQUENA_NEGRITA;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(texto, f), izq, y - 46, 0);
            cb.addTemplate(totalPaginas, izq + f.getBaseFont().getWidthPoint(texto, f.getSize()), y - 46);

            if (borrador) {
                ColumnText.showTextAligned(writer.getDirectContentUnder(), Element.ALIGN_CENTER,
                        new Phrase("BORRADOR", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 90,
                                new Color(0xEE, 0xE6, 0xFF))),
                        pagina.getWidth() / 2, pagina.getHeight() / 2, 45);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(totalPaginas, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber() - 1), PEQUENA_NEGRITA), 0, 0, 0);
        }

        private void barra(PdfContentByte cb, float x, float y, float ancho, float alto) {
            cb.saveState();
            cb.setColorFill(LILA);
            cb.rectangle(x, y, ancho, alto);
            cb.fill();
            cb.restoreState();
        }

        /** Número y fecha; logo, sociedad y contacto (como la proforma). */
        private PdfPTable encabezado() {
            PdfPTable t = new PdfPTable(new float[]{1.25f, 1.45f, 1.5f});
            t.setTotalWidth(PageSize.LETTER.getWidth() - 60);
            t.setLockedWidth(true);

            String rev = c.getRevision() > 1 ? " · Rev. " + c.getRevision() : "";
            String numero = borrador
                    ? (c.getNumero() != null ? c.getNumero() + " (borrador)" : "Borrador")
                    : c.getNumero() + rev;
            PdfPCell titulo = celdaSinBorde();
            titulo.setColspan(2);
            titulo.addElement(new Paragraph("Cotización N° " + numero, TITULO));
            t.addCell(titulo);
            PdfPCell fecha = celdaSinBorde();
            Paragraph pf = new Paragraph();
            pf.add(new Chunk("Fecha de Emisión: ", NEGRITA));
            pf.add(new Chunk(emision(c).atZoneSameInstant(ZONA).format(FECHA_HORA).toLowerCase(), NORMAL));
            pf.setAlignment(Element.ALIGN_RIGHT);
            fecha.addElement(pf);
            t.addCell(fecha);

            t.addCell(separador());

            PdfPCell logoCelda = celdaSinBorde();
            logoCelda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            logoCelda.setFixedHeight(96);
            if (logo != null) {
                try {
                    Image img = Image.getInstance(logo);
                    img.scaleToFit(140, 70);
                    logoCelda.addElement(img);
                } catch (Exception ignorado) {
                    logoCelda.addElement(new Paragraph(nombreComercial, EMPRESA));
                }
            }
            t.addCell(logoCelda);

            PdfPCell sociedad = celdaSinBorde();
            sociedad.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph rs = new Paragraph(razonSocial.toUpperCase(Locale.ROOT), EMPRESA);
            rs.setAlignment(Element.ALIGN_CENTER);
            sociedad.addElement(rs);
            Paragraph nc = new Paragraph(nombreComercial, NORMAL);
            nc.setAlignment(Element.ALIGN_CENTER);
            nc.setSpacingBefore(6);
            sociedad.addElement(nc);
            Paragraph cj = new Paragraph();
            cj.add(new Chunk("Ident. Jurídica: ", NEGRITA));
            cj.add(new Chunk(cedulaJuridica, NORMAL));
            cj.setAlignment(Element.ALIGN_CENTER);
            cj.setSpacingBefore(10);
            sociedad.addElement(cj);
            t.addCell(sociedad);

            PdfPCell contacto = celdaSinBorde();
            contacto.setVerticalAlignment(Element.ALIGN_MIDDLE);
            contacto.addElement(dato("Teléfono: ", telefono));
            contacto.addElement(dato("Correo: ", correo));
            Paragraph dir = dato("Dirección: ", direccion);
            dir.setSpacingBefore(6);
            contacto.addElement(dir);
            t.addCell(contacto);

            t.addCell(separador());
            return t;
        }

        private PdfPCell separador() {
            PdfPCell s = new PdfPCell();
            s.setColspan(3);
            s.setFixedHeight(3);
            s.setBorder(Rectangle.NO_BORDER);
            s.setBackgroundColor(LILA);
            return s;
        }
    }
}
