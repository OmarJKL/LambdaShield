package com.lambdashield.fraude.presentation.dto.response;

import java.math.BigDecimal;

public record MovimientoRecienteResponse(
    String fecha,
    String descripcion,
    boolean esIngreso,
    BigDecimal monto
) {}
