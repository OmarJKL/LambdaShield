package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NotificacionResumen(Long id, String tipo, String titulo, String mensaje, String operacion,
                                  BigDecimal monto, int puntaje, OffsetDateTime fecha, boolean leida) {}
