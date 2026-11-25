package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activa o desactiva reglas del catálogo dejando constancia de quién hizo el cambio. */
@Service
public class EstadoReglaService {
    private final ReglaFraudeRepository repositorio;
    private final CatalogoReglasService catalogo;
    private final AuditoriaService auditoria;

    public EstadoReglaService(ReglaFraudeRepository repositorio, CatalogoReglasService catalogo,
                              AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    @Transactional
    public void establecer(String codigo, boolean activa, String usuario, String direccionIp) {
        var reglas = repositorio.bloquearCatalogo();
        ReglaFraude regla = reglas.stream().filter(r -> r.getCodigoRegla().equals(codigo)).findFirst()
                .orElseThrow(ReglaNoEncontradaException::new);
        if (regla.isEsActiva() == activa) return;
        if (activa) {
            if (!catalogo.implementada(regla)) {
                throw new IllegalArgumentException("No se puede activar una regla sin implementación en el motor.");
            }
            try {
                CatalogoReglasService.pesoEntero(regla);
            } catch (IllegalStateException ex) {
                // Activarla haría fallar todas las evaluaciones nuevas al aplicar el catálogo.
                throw new IllegalArgumentException("No se puede activar " + codigo + ": su peso debe ser un entero entre 0 y 100.");
            }
        } else if (reglas.stream().noneMatch(r -> r != regla && r.isEsActiva())) {
            throw new IllegalArgumentException("Debe quedar al menos una regla activa en el motor.");
        }
        regla.setEsActiva(activa);
        repositorio.save(regla);
        auditoria.registrar(usuario, "regla_fraude", codigo, activa ? "ACTIVAR" : "DESACTIVAR",
                "{\"activa\":" + !activa + "}", "{\"activa\":" + activa + "}", direccionIp);
    }
}
