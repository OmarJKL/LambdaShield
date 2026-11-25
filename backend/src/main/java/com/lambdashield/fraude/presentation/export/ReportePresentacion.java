package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class ReportePresentacion {
    static final Color ROJO = new Color(176, 22, 49);
    static final Color TINTA = new Color(31, 41, 55);
    static final Color GRIS = new Color(100, 116, 139);
    static final Color FONDO = new Color(245, 247, 250);
    static final String[] COLUMNAS = {"ID", "Fecha", "Titular", "Monto (PEN)", "Categoría", "Ubicación", "Hora", "Puntaje", "Nivel", "Estado"};
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private ReportePresentacion() {}
    static String emision() {
        return ZonedDateTime.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (Lima)";
    }
    static String periodo(List<EvaluacionResponse> filas) {
        var fechas = filas.stream().map(EvaluacionResponse::fechaHora).filter(java.util.Objects::nonNull).sorted().toList();
        return fechas.isEmpty() ? "Sin fechas de evaluación disponibles" : "Fechas de evaluación: " + FECHA.format(fechas.getFirst()) + " al " + FECHA.format(fechas.getLast());
    }
    static String moneda(BigDecimal valor) { return "S/ " + String.format(Locale.US, "%,.2f", valor); }
    static String nivel(String valor) { return "critico".equals(valor) ? "CRÍTICO" : valor.toUpperCase(Locale.ROOT); }
    static Color color(String nivel) {
        return switch (nivel) { case "bajo" -> new Color(21, 112, 81); case "medio" -> new Color(151, 100, 10); case "alto" -> new Color(180, 83, 9); default -> ROJO; };
    }
    static long cantidad(List<EvaluacionResponse> filas, String nivel) { return filas.stream().filter(f -> nivel.equals(f.nivel())).count(); }
    static String distribucion(List<EvaluacionResponse> filas) {
        return "Bajo: " + cantidad(filas, "bajo") + "    Medio: " + cantidad(filas, "medio") + "    Alto: " + cantidad(filas, "alto") + "    Crítico: " + cantidad(filas, "critico");
    }
}
