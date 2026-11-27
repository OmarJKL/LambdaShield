package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * CSV plano: encabezado + una fila por evaluación, separador coma, decimales con punto,
 * fechas ISO y UTF-8 con BOM para que Excel respete las tildes.
 */
@Component
public class ReporteCsvExporter {
    private static final String BOM = "﻿";

    public byte[] exportar(List<EvaluacionResponse> filas) {
        StringBuilder csv = new StringBuilder(BOM)
                .append(String.join(",", ReportePresentacion.COLUMNAS)).append("\r\n");
        for (EvaluacionResponse e : filas) {
            csv.append(campo(e.id())).append(',')
                    .append(ReportePresentacion.fecha(e)).append(',')
                    .append(ReportePresentacion.hora(e)).append(',')
                    .append(campo(e.clienteCodigo())).append(',')
                    .append(campo(e.clienteNombre())).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", e.monto())).append(',')
                    .append(campo(e.categoria())).append(',')
                    .append(campo(e.pais())).append(',')
                    .append(e.puntaje()).append(',')
                    .append(campo(ReportePresentacion.nivel(e.nivel()))).append(',')
                    .append(campo(e.estado())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String campo(String valor) {
        return "\"" + ReportePresentacion.texto(valor).replace("\"", "\"\"") + "\"";
    }
}
