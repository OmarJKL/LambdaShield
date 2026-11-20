package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;

/** Serie diaria de ingresos y egresos para gráficos. */
public record MovimientoDiario(String fecha, BigDecimal ingreso, BigDecimal egreso) {}
