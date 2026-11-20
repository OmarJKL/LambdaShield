package com.lambdashield.fraude.presentation.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NotificacionResponse(Long id, String tipo, String titulo, String mensaje, String operacion,
                                   BigDecimal monto, int puntaje, OffsetDateTime fecha, boolean leida) {}
