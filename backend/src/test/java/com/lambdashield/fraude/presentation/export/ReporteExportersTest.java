package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ReporteExportersTest {
    private List<EvaluacionResponse> muestra() {
        String[] niveles = {"bajo", "medio", "alto", "critico"}; int[] puntos = {10, 40, 70, 90};
        return IntStream.range(0, 100).mapToObj(i -> new EvaluacionResponse("TR-" + String.format("%05d", i + 1), "D-101",
                i == 0 ? "=1+1" : "María Fernández de los Ríos y Valdés - Cuenta de demostración",
                new BigDecimal("1234.56"), "transferencia", "regional", 14, puntos[i % 4], niveles[i % 4],
                i % 4 >= 2 ? "Operación retenida" : "Aprobada", "", "", "20/11/2026",
                LocalDateTime.of(2026, 11, 20, 14, 0), List.of(), (long) i + 1, true, BigDecimal.ZERO)).toList();
    }
    @Test void excelConservaDatosNumericosYResumenCalculado() throws Exception {
        byte[] bytes = new ReporteExcelExporter().exportar(muestra());
        Path dir = Path.of("target", "reportes-preview"); Files.createDirectories(dir); Files.write(dir.resolve("evaluaciones.xlsx"), bytes);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(10).getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(hoja.getRow(10).getCell(2).getStringCellValue()).isEqualTo("=1+1");
            assertThat(hoja.getRow(10).getCell(3).getNumericCellValue()).isEqualTo(1234.56);
            assertThat(hoja.getRow(10).getCell(1).getLocalDateTimeCellValue().getYear()).isEqualTo(2026);
            assertThat(hoja.getRow(5).getCell(0).getNumericCellValue()).isEqualTo(100);
            assertThat(hoja.getRow(5).getCell(2).getNumericCellValue()).isCloseTo(123456, within(0.001));
            assertThat(hoja.getRow(5).getCell(5).getNumericCellValue()).isEqualTo(52.5);
            assertThat(hoja.getRow(5).getCell(7).getNumericCellValue()).isEqualTo(50);
            assertThat(hoja.getRow(109).getCell(0).getStringCellValue()).isEqualTo("TR-00100");
            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A10:J110");
        }
    }
    @Test void pdfPaginaTodoElDetalleYRepiteCabeceraYPie() throws Exception {
        byte[] bytes = new ReportePdfExporter().exportar(muestra());
        Files.createDirectories(Path.of("target", "reportes-preview")); Files.write(Path.of("target", "reportes-preview", "evaluaciones.pdf"), bytes);
        try (PdfReader lector = new PdfReader(bytes)) {
            assertThat(lector.getNumberOfPages()).isGreaterThan(2);
            var texto = new PdfTextExtractor(lector);
            assertThat(texto.getTextFromPage(1)).contains("123,456.00", "52.5 / 100", "CRÍTICO");
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                assertThat(texto.getTextFromPage(pagina)).contains("Titular", "Página " + pagina);
            }
            assertThat(texto.getTextFromPage(lector.getNumberOfPages())).contains("TR-00100");
        }
    }
    @Test void filtrosSinResultadosGeneranReportesLegiblesSinErroresDeFormula() throws Exception {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(new ReporteExcelExporter().exportar(List.of())))) {
            assertThat(libro.getSheetAt(0).getRow(5).getCell(0).getNumericCellValue()).isZero();
            assertThat(libro.getSheetAt(0).getRow(5).getCell(5).getStringCellValue()).isEqualTo("Sin datos");
            assertThat(libro.getSheetAt(0).getRow(10).getCell(0).getStringCellValue()).contains("No hay evaluaciones");
        }
        try (var lector = new PdfReader(new ReportePdfExporter().exportar(List.of()))) {
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).contains("No hay evaluaciones", "Sin datos");
        }
    }
}
