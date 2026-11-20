package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Evaluación antifraude expresada en lenguaje de aplicación, sin detalles de interfaz. */
public record EvaluacionResultado(
        String id,
        String clienteCodigo,
        String clienteNombre,
        BigDecimal monto,
        String categoria,
        String pais,
        int hora,
        int puntaje,
        String nivel,
        LocalDateTime fechaEvaluacion,
        List<FactorResultado> factores,
        Long idInterno,
        boolean activa,
    BigDecimal saldoDisponible
) {}
