package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Excel en formato tabla: encabezado en la fila 1 y datos desde la fila 2, sin celdas
 * combinadas ni resúmenes, con tipos reales (fecha, hora, número) listo para tablas dinámicas.
 */
@Component
public class ReporteExcelExporter {
    public byte[] exportar(List<EvaluacionResponse> filas) {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Evaluaciones");
            var formato = libro.createDataFormat();

            var cabecera = libro.createCellStyle();
            var negrita = libro.createFont();
            negrita.setBold(true);
            cabecera.setFont(negrita);
            var fecha = libro.createCellStyle();
            fecha.setDataFormat(formato.getFormat("yyyy-mm-dd"));
            var hora = libro.createCellStyle();
            hora.setDataFormat(formato.getFormat("hh:mm"));
            var monto = libro.createCellStyle();
            monto.setDataFormat(formato.getFormat("#,##0.00"));

            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ReportePresentacion.COLUMNAS.length; i++) {
                Cell c = encabezado.createCell(i);
                c.setCellValue(ReportePresentacion.COLUMNAS[i]);
                c.setCellStyle(cabecera);
            }

            int indice = 1;
            for (var e : filas) {
                Row fila = hoja.createRow(indice++);
                fila.createCell(0).setCellValue(ReportePresentacion.texto(e.id()));
                Cell celdaFecha = fila.createCell(1);
                if (e.fechaHora() != null) {
                    celdaFecha.setCellValue(e.fechaHora().toLocalDate());
                    celdaFecha.setCellStyle(fecha);
                } else {
                    celdaFecha.setCellValue(ReportePresentacion.texto(e.fecha()));
                }
                Cell celdaHora = fila.createCell(2);
                celdaHora.setCellValue(e.hora() / 24.0);
                celdaHora.setCellStyle(hora);
                fila.createCell(3).setCellValue(ReportePresentacion.texto(e.clienteCodigo()));
                fila.createCell(4).setCellValue(ReportePresentacion.texto(e.clienteNombre()));
                Cell celdaMonto = fila.createCell(5);
                celdaMonto.setCellValue(e.monto().doubleValue());
                celdaMonto.setCellStyle(monto);
                fila.createCell(6).setCellValue(ReportePresentacion.texto(e.categoria()));
                fila.createCell(7).setCellValue(ReportePresentacion.texto(e.pais()));
                fila.createCell(8).setCellValue(e.puntaje());
                fila.createCell(9).setCellValue(ReportePresentacion.nivel(e.nivel()));
                fila.createCell(10).setCellValue(ReportePresentacion.texto(e.estado()));
            }

            int ultima = ReportePresentacion.COLUMNAS.length - 1;
            hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, indice - 1), 0, ultima));
            hoja.createFreezePane(0, 1);
            int[] anchos = {12, 12, 8, 15, 40, 14, 16, 14, 9, 10, 24};
            for (int i = 0; i < anchos.length; i++) hoja.setColumnWidth(i, anchos[i] * 256);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el reporte Excel", ex);
        }
    }
}
