package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.fraud.FactorRegla;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadoReglaService {
    private final ReglaFraudeRepository repositorio;
    public EstadoReglaService(ReglaFraudeRepository repositorio) { this.repositorio = repositorio; }

    @Transactional
    public void establecer(String codigo, boolean activa) {
        var regla = repositorio.findByCodigoRegla(codigo).orElseThrow(ReglaNoEncontradaException::new);
        if (activa && FactorRegla.porCodigo(codigo).isEmpty()) {
            throw new IllegalArgumentException("No se puede activar una regla sin implementación en el motor.");
        }
        regla.setEsActiva(activa);
        repositorio.save(regla);
    }
}
