package com.appdomotica.cotizaciones.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Reglas de cálculo de la cotización, sin dependencias para poder probarlas aisladas.
 *
 * <ul>
 *   <li>Margen = recargo sobre el precio neto (SRS §1.3): venta = neto × (1 + margen/100).</li>
 *   <li>Todo en colones con 2 decimales, redondeo HALF_UP.</li>
 *   <li>El IVA se calcula y redondea por línea, y los totales son la suma de las líneas,
 *       para que el desglose del PDF cuadre con el total.</li>
 * </ul>
 */
public final class CalculadoraCotizacion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraCotizacion() {
    }

    public record Linea(BigDecimal precioVenta, BigDecimal subtotal, BigDecimal montoIva, BigDecimal subtotalConIva) {
    }

    public record Totales(BigDecimal totalSinIva, BigDecimal montoIva, BigDecimal totalConIva) {
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal precioVenta(BigDecimal precioNeto, BigDecimal margenPorcentaje) {
        BigDecimal factor = BigDecimal.ONE.add(margenPorcentaje.divide(CIEN, 10, RoundingMode.HALF_UP));
        return redondear(precioNeto.multiply(factor));
    }

    public static Linea linea(BigDecimal precioNeto, BigDecimal margenPorcentaje, int cantidad, BigDecimal ivaPorcentaje) {
        BigDecimal venta = precioVenta(precioNeto, margenPorcentaje);
        BigDecimal subtotal = redondear(venta.multiply(BigDecimal.valueOf(cantidad)));
        BigDecimal iva = redondear(subtotal.multiply(ivaPorcentaje).divide(CIEN, 10, RoundingMode.HALF_UP));
        return new Linea(venta, subtotal, iva, subtotal.add(iva));
    }

    public static Totales totales(List<Linea> lineas) {
        BigDecimal sinIva = BigDecimal.ZERO.setScale(2);
        BigDecimal iva = BigDecimal.ZERO.setScale(2);
        for (Linea l : lineas) {
            sinIva = sinIva.add(l.subtotal());
            iva = iva.add(l.montoIva());
        }
        return new Totales(sinIva, iva, sinIva.add(iva));
    }
}
