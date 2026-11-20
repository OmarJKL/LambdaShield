package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;

/** Movimiento listado en la tabla de actividad reciente. */
public record MovimientoReciente(String fecha, String descripcion, boolean esIngreso, BigDecimal monto) {}
