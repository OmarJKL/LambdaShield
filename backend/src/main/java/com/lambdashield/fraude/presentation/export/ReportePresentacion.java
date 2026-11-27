package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Contrato de columnas compartido por CSV, Excel y PDF: una fila por evaluación,
 * sin totales ni adornos, con valores normalizados para tablas dinámicas, Power BI o pandas.
 */
final class ReportePresentacion {
    static final String[] COLUMNAS = {"ID", "Fecha", "Hora", "Codigo cliente", "Cliente", "Monto PEN",
            "Categoria", "Ubicacion", "Puntaje", "Nivel", "Estado"};
    static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private ReportePresentacion() {}

    /** Fecha ISO (yyyy-MM-dd): ordenable y reconocida por cualquier herramienta. */
    static String fecha(EvaluacionResponse e) {
        return e.fechaHora() != null ? FECHA_ISO.format(e.fechaHora()) : texto(e.fecha());
    }

    static String hora(EvaluacionResponse e) {
        return String.format(Locale.ROOT, "%02d:00", e.hora());
    }

    /** Nivel como código estable (BAJO, MEDIO, ALTO, CRITICO) para agrupar sin variantes. */
    static String nivel(String valor) {
        return valor == null ? "" : valor.toUpperCase(Locale.ROOT);
    }

    static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
