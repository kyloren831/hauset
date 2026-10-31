package com.appdomotica.cotizaciones;

import com.appdomotica.cotizaciones.service.MontoEnLetras;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MontoEnLetrasTest {
    private static String l(String monto) {
        return MontoEnLetras.colones(new BigDecimal(monto));
    }

    @Test
    void comoEnLaProforma() {
        assertEquals("CIENTO DOCE MIL NOVENTA Y SEIS COLONES.", l("112096.00"));
    }

    @Test
    void casosEspeciales() {
        assertEquals("UN COLÓN.", l("1"));
        assertEquals("VEINTIÚN COLONES.", l("21"));
        assertEquals("CIEN COLONES.", l("100"));
        assertEquals("CIENTO UN COLONES.", l("101"));
        assertEquals("MIL COLONES.", l("1000"));
        assertEquals("VEINTIÚN MIL QUINIENTOS COLONES.", l("21500"));
        assertEquals("UN MILLÓN DE COLONES.", l("1000000"));
        assertEquals("DOS MILLONES TRESCIENTOS CUARENTA Y CINCO MIL SEISCIENTOS SETENTA Y OCHO COLONES CON 90/100.",
                l("2345678.90"));
        assertEquals("CERO COLONES CON 50/100.", l("0.5"));
    }
}
