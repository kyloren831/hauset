package com.appdomotica.mantenimiento.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Cifra las contraseñas de los equipos (RF-MAN-04): AES-256-GCM, un nonce nuevo por valor y el id de la
 * credencial como dato autenticado, para que un valor copiado a otra fila no se pueda descifrar.
 * Formato guardado: "v1:" + Base64(nonce ‖ cifrado con etiqueta).
 *
 * La llave (32 bytes en Base64) viene de HAUSET_CREDENCIALES_LLAVE, fuera de la base y del repositorio
 * (RNF-SEG-05). Sin ella la aplicación no arranca.
 */
@Component
public class CifradoCredenciales {
    private static final String PREFIJO = "v1:";
    private static final int NONCE = 12;
    private static final int ETIQUETA_BITS = 128;

    private final SecretKeySpec llave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoCredenciales(@Value("${hauset.credenciales.llave}") String llaveBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(llaveBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("HAUSET_CREDENCIALES_LLAVE no es Base64 válido");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("HAUSET_CREDENCIALES_LLAVE debe tener 32 bytes (AES-256)");
        }
        this.llave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(UUID credencialId, String contrasena) {
        try {
            byte[] nonce = new byte[NONCE];
            azar.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA_BITS, nonce));
            c.updateAAD(aad(credencialId));
            byte[] cifrado = c.doFinal(contrasena.getBytes(StandardCharsets.UTF_8));
            return PREFIJO + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(NONCE + cifrado.length).put(nonce).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la contraseña", e);
        }
    }

    public String descifrar(UUID credencialId, String guardado) {
        if (guardado == null || !guardado.startsWith(PREFIJO)) {
            throw new IllegalStateException("La contraseña guardada no tiene un formato conocido");
        }
        try {
            byte[] todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA_BITS, todo, 0, NONCE));
            c.updateAAD(aad(credencialId));
            return new String(c.doFinal(todo, NONCE, todo.length - NONCE), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar la contraseña (¿cambió la llave?)", e);
        }
    }

    private static byte[] aad(UUID id) {
        return id.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
