package com.lambdashield.fraude.presentation.dto.response;

import java.math.BigDecimal;

public record MovimientoDiarioResponse(
    String fecha,
    BigDecimal ingreso,
    BigDecimal egreso
) {}
