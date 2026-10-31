package com.appdomotica.cotizaciones.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 112096.00 → "CIENTO DOCE MIL NOVENTA Y SEIS COLONES" (con céntimos: "... CON 50/100"). */
public final class MontoEnLetras {
    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO",
            "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO",
            "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO",
            "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {
    }

    public static String colones(BigDecimal monto) {
        BigDecimal m = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centimos = m.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String letras = entero == 0 ? "CERO" : numero(entero);
        // "UN MILLÓN DE COLONES", "VEINTIÚN COLONES"
        if (letras.endsWith("UNO")) {
            letras = letras.substring(0, letras.length() - 3) + (letras.endsWith("VEINTIUNO") ? "ÚN" : "UN");
        }
        String de = (entero % 1_000_000 == 0 && entero >= 1_000_000) ? " DE" : "";
        String texto = letras + de + (entero == 1 ? " COLÓN" : " COLONES");
        return centimos == 0 ? texto + "." : texto + " CON " + String.format("%02d", centimos) + "/100.";
    }

    static String numero(long n) {
        if (n >= 1_000_000) {
            long millones = n / 1_000_000;
            String m = millones == 1 ? "UN MILLÓN" : apocopar(numero(millones)) + " MILLONES";
            return n % 1_000_000 == 0 ? m : m + " " + numero(n % 1_000_000);
        }
        if (n >= 1000) {
            long miles = n / 1000;
            String m = miles == 1 ? "MIL" : apocopar(numero(miles)) + " MIL";
            return n % 1000 == 0 ? m : m + " " + numero(n % 1000);
        }
        if (n >= 100) {
            if (n == 100) {
                return "CIEN";
            }
            String c = CENTENAS[(int) (n / 100)];
            return n % 100 == 0 ? c : c + " " + numero(n % 100);
        }
        if (n < 30) {
            return UNIDADES[(int) n];
        }
        String d = DECENAS[(int) (n / 10)];
        return n % 10 == 0 ? d : d + " Y " + UNIDADES[(int) (n % 10)];
    }

    /** Antes de "MIL" o "MILLONES": "UNO" → "UN", "VEINTIUNO" → "VEINTIÚN". */
    private static String apocopar(String s) {
        if (s.endsWith("VEINTIUNO")) {
            return s.substring(0, s.length() - 9) + "VEINTIÚN";
        }
        return s.endsWith("UNO") ? s.substring(0, s.length() - 3) + "UN" : s;
    }
}
