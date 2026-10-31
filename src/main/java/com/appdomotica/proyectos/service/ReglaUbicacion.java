package com.appdomotica.proyectos.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuándo una ubicación con checklist queda completada. Se calcula siempre, nunca se guarda: así no puede
 * quedar desincronizado con sus puntos.
 *
 * - Todos los puntos marcados (desmarcar uno la devuelve a pendiente).
 * - Al menos una foto de antes y una de después de la ubicación.
 * - Las tareas que tenga en esa ubicación (por ejemplo, agregadas con un cambio de instrucciones) terminadas.
 *
 * Una ubicación sin checklist se completa como antes: con sus tareas, una por producto.
 */
public final class ReglaUbicacion {
    private ReglaUbicacion() {
    }

    public static boolean completada(int puntos, int marcados, int fotosAntes, int fotosDespues, int tareasPendientes) {
        return puntos > 0 && marcados == puntos && fotosAntes > 0 && fotosDespues > 0 && tareasPendientes == 0;
    }

    /** Qué le falta a una ubicación con checklist (vacío = completada). Las tareas pendientes se cuentan aparte. */
    public static List<String> falta(int numero, int puntos, int marcados, int fotosAntes, int fotosDespues) {
        List<String> falta = new ArrayList<>();
        int sinMarcar = puntos - marcados;
        if (sinMarcar > 0) {
            falta.add(sinMarcar == 1 ? "1 punto sin marcar" : sinMarcar + " puntos sin marcar");
        }
        if (fotosAntes == 0) {
            falta.add("foto de antes");
        }
        if (fotosDespues == 0) {
            falta.add("foto de después");
        }
        return falta.isEmpty() ? List.of() : List.of("Ubicación " + numero + ": falta " + String.join(", ", falta));
    }
}
