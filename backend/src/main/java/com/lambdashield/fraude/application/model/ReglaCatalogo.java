package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;

/**
 * Regla del catálogo persistido.
 *
 * @param pesoMaximo    peso relativo (entero 0-100); null si el valor guardado no es válido
 * @param participacion porcentaje del puntaje que reparte hoy entre las activas; null si está inactiva
 * @param personalizada true para reglas RC creadas desde el formulario
 */
public record ReglaCatalogo(String codigo, String nombre, String descripcion, Integer pesoMaximo, boolean activa,
                            BigDecimal participacion, boolean personalizada) {}
