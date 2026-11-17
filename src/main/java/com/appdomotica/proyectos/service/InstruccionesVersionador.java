package com.appdomotica.proyectos.service;

import com.appdomotica.catalogo.entity.ProductoCatalogo;
import com.appdomotica.proyectos.entity.ChecklistTarea;
import com.appdomotica.proyectos.entity.InstruccionItem;
import com.appdomotica.proyectos.entity.InstruccionVersion;
import com.appdomotica.proyectos.repository.InstruccionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** Arma el contenido de una versión nueva de las instrucciones (cambio en sitio o versión nueva del informe). */
@Component
@RequiredArgsConstructor
class InstruccionesVersionador {
    private final InstruccionItemRepository items;

    /**
     * Contenido de la versión nueva: lo vigente agrupado por producto (o por descripción si es una tarea
     * libre), comparado con la versión anterior para marcar qué se agregó, cambió o quitó.
     */
    void copiarInstrucciones(InstruccionVersion anterior, InstruccionVersion nueva, List<ChecklistTarea> vigentes) {
        Map<String, Integer> ahora = new LinkedHashMap<>();
        Map<String, ProductoCatalogo> producto = new HashMap<>();
        Map<String, String> libre = new HashMap<>();
        for (ChecklistTarea t : vigentes) {
            String clave = t.getProducto() != null ? "p:" + t.getProducto().getId() : "d:" + t.getDescripcion();
            ahora.merge(clave, t.getCantidad() != null ? t.getCantidad() : 1, Integer::sum);
            if (t.getProducto() != null) {
                producto.put(clave, t.getProducto());
            } else {
                libre.put(clave, t.getDescripcion());
            }
        }
        int orden = 0;
        Set<String> vistas = new HashSet<>();
        for (InstruccionItem previo : items.findByInstruccionVersionIdOrderByOrdenAsc(anterior.getId())) {
            if ("QUITADO".equals(previo.getAccion())) {
                continue;
            }
            String clave = previo.getProducto() != null ? "p:" + previo.getProducto().getId() : "d:" + previo.getDescripcion();
            vistas.add(clave);
            Integer cantidad = ahora.get(clave);
            InstruccionItem i = new InstruccionItem();
            i.setInstruccionVersion(nueva);
            i.setOrden(orden++);
            i.setProducto(previo.getProducto());
            i.setOrigenItemId(previo.getId());
            if (cantidad == null) {
                i.setCantidad(previo.getCantidad());
                i.setDescripcion(previo.getDescripcion());
                i.setAccion("QUITADO");
            } else {
                i.setCantidad(cantidad);
                i.setDescripcion(previo.getProducto() != null
                        ? CambioInstruccionService.recortar("Instalar " + cantidad + " × " + previo.getProducto().getNombre())
                        : previo.getDescripcion());
                i.setAccion(cantidad.equals(previo.getCantidad()) ? "SIN_CAMBIO" : "MODIFICADO");
            }
            items.save(i);
        }
        for (var e : ahora.entrySet()) {
            if (vistas.contains(e.getKey())) {
                continue;
            }
            ProductoCatalogo prod = producto.get(e.getKey());
            InstruccionItem i = new InstruccionItem();
            i.setInstruccionVersion(nueva);
            i.setOrden(orden++);
            i.setProducto(prod);
            i.setCantidad(e.getValue());
            i.setDescripcion(prod != null ? CambioInstruccionService.recortar("Instalar " + e.getValue() + " × " + prod.getNombre())
                    : libre.get(e.getKey()));
            i.setAccion("AGREGADO");
            items.save(i);
        }
    }
}
