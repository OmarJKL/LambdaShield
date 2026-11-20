package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;

/** Balance del periodo consultado con sus variaciones porcentuales. */
public record ResumenFinanciero(
        BigDecimal saldo,
        BigDecimal ingresos,
        BigDecimal egresos,
        double variacionSaldo,
        double variacionIngresos,
        double variacionEgresos
) {}
