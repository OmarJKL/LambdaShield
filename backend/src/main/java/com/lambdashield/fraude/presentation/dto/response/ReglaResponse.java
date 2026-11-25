package com.lambdashield.fraude.presentation.dto.response;

import java.math.BigDecimal;

/** pesoMaximo es el peso relativo; participacion, el % del puntaje que aporta hoy si está activa. */
public record ReglaResponse(
    String codigo,
    String nombre,
    String descripcion,
    Integer pesoMaximo,
    boolean activa,
    BigDecimal participacion,
    boolean personalizada
) {}
