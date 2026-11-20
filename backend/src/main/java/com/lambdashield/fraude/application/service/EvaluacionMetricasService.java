package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.KpisResultado;
import com.lambdashield.fraude.repository.EvaluacionFraudeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluacionMetricasService {
    private final EvaluacionFraudeRepository repositorio;
    public EvaluacionMetricasService(EvaluacionFraudeRepository repositorio) { this.repositorio = repositorio; }
    @Transactional(readOnly = true)
    public KpisResultado calcularKpis() {
        var m = repositorio.calcularMetricas();
        return new KpisResultado(m.getTotal(), m.getAprobadas(), m.getMonitoreadas(), m.getEnRevision(),
                m.getBloqueadas(), m.getPromedio() == null ? 0 : m.getPromedio());
    }
}
