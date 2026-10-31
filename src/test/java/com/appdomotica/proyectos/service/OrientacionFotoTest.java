package com.appdomotica.proyectos.service;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrientacionFotoTest {
    /** JPEG mínimo con un bloque EXIF (little endian) que solo trae Orientation = 6. */
    private static byte[] jpegConOrientacion(int o) {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,
                0x01, 0x00,
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) o, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDA};
    }

    @Test
    void leeLaOrientacionYGiraLaFoto() {
        assertEquals(6, ReporteEntregaPdfService.orientacionExif(jpegConOrientacion(6)));
        assertEquals(1, ReporteEntregaPdfService.orientacionExif(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA}));
        assertEquals(1, ReporteEntregaPdfService.orientacionExif(new byte[]{1, 2, 3}));
        BufferedImage acostada = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        BufferedImage parada = ReporteEntregaPdfService.enderezar(acostada, 6);
        assertEquals(20, parada.getWidth());
        assertEquals(40, parada.getHeight());
    }
}
