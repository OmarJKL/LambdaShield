package com.lambdashield.fraude.presentation.dto.response;

import java.math.BigDecimal;

public record DashboardResponse(
    BigDecimal saldo,
    BigDecimal ingresos,
    BigDecimal egresos,
    double variacionSaldo,
    double variacionIngresos,
    double variacionEgresos
) {}
