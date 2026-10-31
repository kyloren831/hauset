package com.appdomotica.multimedia.service;

import com.appdomotica.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

/** Reglas comunes para fotos que suben los usuarios: imagen, no vacía y como mucho 8 MB. */
public final class ValidadorImagen {
    public static final long MAX_BYTES = 8L * 1024 * 1024;

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
    }
}
