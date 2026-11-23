package com.appdomotica.proyectos.service;

import com.appdomotica.multimedia.service.ValidadorImagen;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Fotos con dimensiones enormes y pocos bytes (auditoría, pista 5). */
class FotoGiganteTest {

    /** PNG negro escrito a mano para no reservarlo en memoria. */
    static byte[] png(int ancho, int alto) throws Exception {
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(idat, new Deflater(9))) {
            byte[] fila = new byte[1 + ancho * 3];
            for (int y = 0; y < alto; y++) {
                z.write(fila);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(png);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(ihdr);
        h.writeInt(ancho);
        h.writeInt(alto);
        h.write(new byte[]{8, 2, 0, 0, 0});
        chunk(out, "IHDR", ihdr.toByteArray());
        chunk(out, "IDAT", idat.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(DataOutputStream out, String tipo, byte[] datos) throws Exception {
        byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(datos.length);
        out.write(t);
        out.write(datos);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(datos);
        out.writeInt((int) crc.getValue());
    }

    @Test
    void elValidadorRechazaMasDe40Megapixeles() throws Exception {
        byte[] gigante = png(12000, 12000);
        assertThrows(Exception.class, () ->
                ValidadorImagen.validar(new MockMultipartFile("f", "g.png", "image/png", gigante)));
        assertDoesNotThrow(() ->
                ValidadorImagen.validar(new MockMultipartFile("f", "n.png", "image/png", png(1600, 1200))));
        // Lo que Java no sabe leer pasa como antes (no se decodifica en la validación).
        assertDoesNotThrow(() ->
                ValidadorImagen.validar(new MockMultipartFile("f", "x.heic", "image/heic", new byte[]{1, 2, 3})));
    }

    @Test
    void elReporteDecodificaReducidoSinReservarElRasterCompleto() throws Exception {
        // Una foto que ya estaba guardada antes del límite: 12000x12000 serían ~430 MB sin submuestreo.
        BufferedImage reducida = ReporteEntregaPdfService.leerReducida(png(12000, 12000));
        assertNotNull(reducida);
        assertTrue(reducida.getWidth() <= 1800 * 2 && reducida.getWidth() >= 900, "ancho " + reducida.getWidth());
        assertNotNull(ReporteEntregaPdfService.imagen(png(12000, 12000)));
        // Una foto normal no se submuestrea.
        assertEquals(1600, ReporteEntregaPdfService.leerReducida(png(1600, 1200)).getWidth());
    }
}
