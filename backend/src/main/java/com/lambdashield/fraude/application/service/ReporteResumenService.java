package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.EvaluacionResultado;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReporteResumenService {

    public record ResumenReporte(
            long total,
            long aprobadas,
            long monitoreadas,
            long enRevision,
            long bloqueadas,
            BigDecimal montoTotal,
            BigDecimal montoBloqueado,
            double tasaBloqueo
    ) {}

    public ResumenReporte resumir(List<EvaluacionResultado> transacciones) {
        long total = transacciones.size();
        long aprobadas = transacciones.stream().filter(t -> "bajo".equals(t.nivel())).count();
        long monitoreadas = transacciones.stream().filter(t -> "medio".equals(t.nivel())).count();
        long enRevision = transacciones.stream().filter(t -> "alto".equals(t.nivel())).count();
        long bloqueadas = transacciones.stream().filter(t -> "critico".equals(t.nivel())).count();

        BigDecimal montoTotal = transacciones.stream().map(EvaluacionResultado::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montoBloqueado = transacciones.stream()
                .filter(t -> "critico".equals(t.nivel()) || "alto".equals(t.nivel()))
                .map(EvaluacionResultado::monto).reduce(BigDecimal.ZERO, BigDecimal::add);

        double tasaBloqueo = total > 0 ? ((double) bloqueadas / total) * 100.0 : 0.0;

        return new ResumenReporte(total, aprobadas, monitoreadas, enRevision, bloqueadas,
                montoTotal, montoBloqueado, tasaBloqueo);
    }
}
