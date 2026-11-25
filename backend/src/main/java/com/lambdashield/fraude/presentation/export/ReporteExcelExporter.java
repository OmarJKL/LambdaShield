package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.util.List;

/** Reporte Excel con datos tipados, filtros y diseño de impresión. */
@Component
public class ReporteExcelExporter {
    public byte[] exportar(List<EvaluacionResponse> filas) {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Evaluaciones");
            hoja.setDisplayGridlines(false);
            var titulo = estilo(libro, 21, true, Color.WHITE, ReportePresentacion.ROJO);
            var subtitulo = estilo(libro, 10, false, ReportePresentacion.GRIS, null);
            var etiqueta = estilo(libro, 10, true, ReportePresentacion.GRIS, ReportePresentacion.FONDO);
            var indicador = estilo(libro, 18, true, ReportePresentacion.TINTA, ReportePresentacion.FONDO);
            indicador.setAlignment(HorizontalAlignment.LEFT); indicador.setIndention((short) 1);
            etiqueta.setIndention((short) 1);
            var importeIndicador = libro.createCellStyle(); importeIndicador.cloneStyleFrom(indicador);
            importeIndicador.setDataFormat(libro.createDataFormat().getFormat("\"S/ \"#,##0.00"));
            var promedioIndicador = libro.createCellStyle(); promedioIndicador.cloneStyleFrom(indicador);
            promedioIndicador.setDataFormat(libro.createDataFormat().getFormat("0.0"));
            var cabecera = estilo(libro, 10, true, Color.WHITE, ReportePresentacion.TINTA);
            cabecera.setAlignment(HorizontalAlignment.CENTER);
            combinar(hoja, 0, 0, 9, "LAMBDA SHIELD", titulo); hoja.getRow(0).setHeightInPoints(42);
            combinar(hoja, 1, 0, 9, "Reporte de evaluaciones antifraude", subtitulo);
            combinar(hoja, 2, 0, 9, ReportePresentacion.periodo(filas) + "    Emitido: " + ReportePresentacion.emision(), subtitulo);
            int[][] bloques = {{0, 1}, {2, 4}, {5, 6}, {7, 9}};
            String[] etiquetas = {"EVALUACIONES", "MONTO EVALUADO (PEN)", "PUNTAJE PROMEDIO", "ALTO + CRÍTICO"};
            int fin = 10 + filas.size();
            String[] formulas = {"COUNTA(A11:A" + fin + ")", "SUM(D11:D" + fin + ")", "AVERAGE(H11:H" + fin + ")", "COUNTIF(I11:I" + fin + ",\"ALTO\")+COUNTIF(I11:I" + fin + ",\"CRÍTICO\")"};
            for (int i = 0; i < bloques.length; i++) {
                combinar(hoja, 4, bloques[i][0], bloques[i][1], etiquetas[i], etiqueta);
                Cell celda = combinar(hoja, 5, bloques[i][0], bloques[i][1], "", i == 1 ? importeIndicador : i == 2 ? promedioIndicador : indicador);
                if (filas.isEmpty()) { if (i == 2) celda.setCellValue("Sin datos"); else celda.setCellValue(0); }
                else celda.setCellFormula(formulas[i]);
            }
            hoja.getRow(5).setHeightInPoints(35);
            combinar(hoja, 7, 0, 9, ReportePresentacion.distribucion(filas), subtitulo);
            Row encabezado = hoja.createRow(9); encabezado.setHeightInPoints(28);
            for (int i = 0; i < 10; i++) { Cell c = encabezado.createCell(i); c.setCellValue(ReportePresentacion.COLUMNAS[i]); c.setCellStyle(cabecera); }
            XSSFCellStyle[][] datos = new XSSFCellStyle[2][4];
            XSSFCellStyle[][] riesgos = new XSSFCellStyle[2][4];
            String[] niveles = {"bajo", "medio", "alto", "critico"};
            for (int banda = 0; banda < 2; banda++) {
                Color fondo = banda == 0 ? Color.WHITE : ReportePresentacion.FONDO;
                for (int tipo = 0; tipo < 4; tipo++) {
                    datos[banda][tipo] = estilo(libro, 10, false, ReportePresentacion.TINTA, fondo);
                    datos[banda][tipo].setIndention((short) 1);
                }
                datos[banda][1].setDataFormat(libro.createDataFormat().getFormat("\"S/ \"#,##0.00"));
                datos[banda][1].setAlignment(HorizontalAlignment.RIGHT);
                datos[banda][2].setAlignment(HorizontalAlignment.CENTER);
                datos[banda][2].setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
                datos[banda][3].setAlignment(HorizontalAlignment.CENTER);
                datos[banda][3].setDataFormat(libro.createDataFormat().getFormat("hh:mm"));
                for (int i = 0; i < niveles.length; i++) riesgos[banda][i] = estilo(libro, 10, true, ReportePresentacion.color(niveles[i]), fondo);
            }
            int indice = 10;
            for (var evaluacion : filas) {
                Row fila = hoja.createRow(indice); fila.setHeightInPoints(Math.max(32, (float) Math.ceil(evaluacion.clienteNombre().length() / 34.0) * 14)); int banda = (indice++ - 10) % 2;
                Object[] valores = {evaluacion.id(), evaluacion.fechaHora() != null ? evaluacion.fechaHora() : evaluacion.fecha(), evaluacion.clienteNombre(), evaluacion.monto().doubleValue(), evaluacion.categoria(), evaluacion.pais(), evaluacion.hora() / 24.0, evaluacion.puntaje(), ReportePresentacion.nivel(evaluacion.nivel()), evaluacion.estado()};
                for (int columna = 0; columna < 10; columna++) {
                    Cell celda = fila.createCell(columna); Object valor = valores[columna];
                    if (valor instanceof Number numero) celda.setCellValue(numero.doubleValue());
                    else if (valor instanceof java.time.LocalDateTime fecha) celda.setCellValue(fecha);
                    else celda.setCellValue(valor == null ? "" : valor.toString());
                    celda.setCellStyle(datos[banda][columna == 3 ? 1 : columna == 1 ? 2 : columna == 6 ? 3 : 0]);
                    if (columna == 8) { int riesgo = java.util.Arrays.asList(niveles).indexOf(evaluacion.nivel()); if (riesgo >= 0) celda.setCellStyle(riesgos[banda][riesgo]); }
                }
            }
            if (filas.isEmpty()) combinar(hoja, 10, 0, 9, "No hay evaluaciones para los filtros seleccionados.", subtitulo);
            hoja.setAutoFilter(new CellRangeAddress(9, Math.max(9, indice - 1), 0, 9));
            hoja.createFreezePane(3, 10);
            int[] anchos = {18, 15, 38, 21, 20, 19, 11, 12, 15, 25};
            for (int i = 0; i < anchos.length; i++) hoja.setColumnWidth(i, anchos[i] * 256);
            hoja.setRepeatingRows(new CellRangeAddress(9, 9, -1, -1));
            hoja.getPrintSetup().setLandscape(true); hoja.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            hoja.getPrintSetup().setFitWidth((short) 1); hoja.getPrintSetup().setFitHeight((short) 0); hoja.setFitToPage(true);
            hoja.getHeader().setRight("Lambda Shield"); hoja.getFooter().setRight("Página &P de &N");
            libro.setPrintArea(0, 0, 9, 0, Math.max(10, indice - 1));
            libro.getCreationHelper().createFormulaEvaluator().evaluateAll();
            libro.write(salida); return salida.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException("No se pudo generar el reporte Excel", ex); }
    }
    private static XSSFCellStyle estilo(XSSFWorkbook libro, int tamano, boolean negrita, Color texto, Color fondo) {
        var estilo = libro.createCellStyle(); var fuente = libro.createFont();
        fuente.setFontName("Calibri"); fuente.setFontHeightInPoints((short) tamano); fuente.setBold(negrita);
        fuente.setColor(new XSSFColor(texto, null)); estilo.setFont(fuente); estilo.setVerticalAlignment(VerticalAlignment.CENTER); estilo.setWrapText(true);
        if (fondo != null) { estilo.setFillForegroundColor(new XSSFColor(fondo, null)); estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
        return estilo;
    }
    private static Cell combinar(XSSFSheet hoja, int fila, int desde, int hasta, String valor, CellStyle estilo) {
        Row row = hoja.getRow(fila); if (row == null) row = hoja.createRow(fila);
        for (int i = desde; i <= hasta; i++) { Cell c = row.createCell(i); c.setCellStyle(estilo); }
        hoja.addMergedRegion(new CellRangeAddress(fila, fila, desde, hasta));
        Cell celda = row.getCell(desde); celda.setCellValue(valor); return celda;
    }
}
