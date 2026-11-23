package com.appdomotica.multimedia.service;

import com.appdomotica.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * Reglas comunes para fotos que suben los usuarios: imagen, no vacía, como mucho 8 MB y 40 megapíxeles.
 * Un PNG de pocos KB puede declarar 12000x12000 y ocupar cientos de MB al decodificarse (auditoría, pista 5).
 */
public final class ValidadorImagen {
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    /** La app manda fotos de 1600 px; 40 MP deja margen de sobra para cualquier cámara. */
    public static final long MAX_PIXELES = 40_000_000L;

    private ValidadorImagen() {
    }

    public static void validar(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new BadRequestException("La foto está vacía");
        }
        if (foto.getContentType() == null || !foto.getContentType().startsWith("image/")) {
            throw new BadRequestException("El archivo debe ser una imagen");
        }
        if (foto.getSize() > MAX_BYTES) {
            throw new BadRequestException("La foto supera 8 MB; comprímela antes de subirla");
        }
        Optional<Dimension> d;
        try (InputStream in = foto.getInputStream()) {
            d = dimensiones(in);
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer la foto");
        }
        // Formatos que Java no sabe leer (HEIC, por ejemplo) pasan como antes: no se decodifican aquí.
        if (d.isPresent() && (long) d.get().width * d.get().height > MAX_PIXELES) {
            throw new BadRequestException("La foto tiene demasiados píxeles (" + d.get().width + "x" + d.get().height
                    + "); redúcela antes de subirla");
        }
    }

    /** Ancho y alto declarados en la cabecera, sin decodificar los píxeles. */
    public static Optional<Dimension> dimensiones(byte[] bytes) {
        try {
            return dimensiones(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<Dimension> dimensiones(InputStream in) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(iis);
            if (!lectores.hasNext()) {
                return Optional.empty();
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(iis, true, true);
                return Optional.of(new Dimension(lector.getWidth(0), lector.getHeight(0)));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            } finally {
                lector.dispose();
            }
        }
    }
}
