package com.appdomotica.proyectos.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReglaUbicacionTest {

    @Test
    void completadaConTodosLosPuntosMarcadosYFotos() {
        assertTrue(ReglaUbicacion.completada(3, 3, 1, 1, 0));
    }

    @Test
    void unPuntoDesmarcadoLaDejaPendiente() {
        assertFalse(ReglaUbicacion.completada(3, 2, 1, 1, 0));
    }

    @Test
    void sinFotoDeAntesODeDespuesSiguePendiente() {
        assertFalse(ReglaUbicacion.completada(3, 3, 0, 1, 0));
        assertFalse(ReglaUbicacion.completada(3, 3, 1, 0, 0));
    }

    @Test
    void unaTareaPendienteEnLaUbicacionTambienCuenta() {
        assertFalse(ReglaUbicacion.completada(3, 3, 1, 1, 1));
    }

    @Test
    void sinChecklistNoAplicaEstaRegla() {
        assertFalse(ReglaUbicacion.completada(0, 0, 1, 1, 0));
    }

    @Test
    void diceQueFalta() {
        assertEquals(List.of("Ubicación 2: falta 2 puntos sin marcar, foto de después"),
                ReglaUbicacion.falta(2, 3, 1, 1, 0));
        assertEquals(List.of(), ReglaUbicacion.falta(1, 2, 2, 1, 1));
    }
}
