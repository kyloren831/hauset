package com.appdomotica.cotizaciones;

import com.appdomotica.cotizaciones.service.CalculadoraCotizacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculadoraCotizacionTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void elMargenEsUnRecargoSobreElNetoNoUnMargenSobreLaVenta() {
        // 30 % sobre 1000 de neto = 1300 de venta (en Odoo, "30 %" sería margen sobre venta: 1428,57).
        assertEquals(bd("1300.00"), CalculadoraCotizacion.precioVenta(bd("1000"), bd("30")));
    }

    @Test
    void redondeaAColonesConDosDecimalesHaciaArriba() {
        // 33,33 × 1,15 = 38,3295 → 38,33
        assertEquals(bd("38.33"), CalculadoraCotizacion.precioVenta(bd("33.33"), bd("15")));
        assertEquals(bd("0.01"), CalculadoraCotizacion.precioVenta(bd("0.01"), bd("0")));
    }

    @Test
    void lineaCalculaSubtotalEIvaPorLinea() {
        var l = CalculadoraCotizacion.linea(bd("1000"), bd("30"), 3, bd("13"));
        assertEquals(bd("1300.00"), l.precioVenta());
        assertEquals(bd("3900.00"), l.subtotal());
        assertEquals(bd("507.00"), l.montoIva());
        assertEquals(bd("4407.00"), l.subtotalConIva());
    }

    @Test
    void ivaCeroDejaElTotalIgualAlSubtotal() {
        var l = CalculadoraCotizacion.linea(bd("14750.50"), bd("20"), 2, bd("0"));
        assertEquals(bd("35401.20"), l.subtotal());
        assertEquals(bd("0.00"), l.montoIva());
        assertEquals(l.subtotal(), l.subtotalConIva());
    }

    @Test
    void losTotalesSonLaSumaDeLasLineasRedondeadas() {
        // Cada línea redondea su IVA: 0,13 × 1,05 = 0,1365 → 0,14 por línea; dos líneas = 0,28
        var a = CalculadoraCotizacion.linea(bd("1.05"), bd("0"), 1, bd("13"));
        var b = CalculadoraCotizacion.linea(bd("1.05"), bd("0"), 1, bd("13"));
        var t = CalculadoraCotizacion.totales(List.of(a, b));
        assertEquals(bd("2.10"), t.totalSinIva());
        assertEquals(bd("0.28"), t.montoIva());
        assertEquals(bd("2.38"), t.totalConIva());
    }

    @Test
    void sinLineasLosTotalesSonCero() {
        var t = CalculadoraCotizacion.totales(List.of());
        assertEquals(bd("0.00"), t.totalConIva());
    }
}
