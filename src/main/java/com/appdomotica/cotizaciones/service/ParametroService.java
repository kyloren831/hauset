package com.appdomotica.cotizaciones.service;

import com.appdomotica.cotizaciones.entity.ParametroSistema;
import com.appdomotica.cotizaciones.repository.ParametroSistemaRepository;
import com.appdomotica.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Parámetros configurables por los jefes. El IVA por defecto solo afecta a cotizaciones nuevas. */
@Service
@RequiredArgsConstructor
public class ParametroService {
    private static final BigDecimal IVA_INICIAL = new BigDecimal("13.00");
    private final ParametroSistemaRepository repo;

    @Transactional(readOnly = true)
    public BigDecimal ivaPorDefecto() {
        return repo.findById(ParametroSistema.IVA_DEFECTO)
                .map(p -> new BigDecimal(p.getValor()))
                .orElse(IVA_INICIAL);
    }

    @Transactional
    public BigDecimal cambiarIvaPorDefecto(BigDecimal porcentaje, Usuario usuario) {
        ParametroSistema p = repo.findById(ParametroSistema.IVA_DEFECTO).orElseGet(() -> {
            ParametroSistema nuevo = new ParametroSistema();
            nuevo.setClave(ParametroSistema.IVA_DEFECTO);
            return nuevo;
        });
        BigDecimal valor = CalculadoraCotizacion.redondear(porcentaje);
        p.setValor(valor.toPlainString());
        p.setActualizadoPor(usuario);
        p.setUpdatedAt(OffsetDateTime.now());
        repo.save(p);
        return valor;
    }
}
