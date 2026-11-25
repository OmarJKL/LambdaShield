package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Reporte paginado con resumen ejecutivo y detalle completo de evaluaciones. */
@Component
public class ReportePdfExporter {
    public byte[] exportar(List<EvaluacionResponse> filas) {
        var salida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4.rotate(), 32, 32, 30, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                            new Phrase("LAMBDA SHIELD  /  Evaluaciones antifraude", fuente(8, false, ReportePresentacion.GRIS)), d.left(), 20, 0);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Página " + w.getPageNumber(), fuente(8, false, ReportePresentacion.GRIS)), d.right(), 20, 0);
                }
            });
            documento.addTitle("Reporte de evaluaciones antifraude"); documento.addAuthor("Lambda Shield");
            documento.open();
            texto(documento, "LAMBDA SHIELD", 10, true, ReportePresentacion.ROJO, 5);
            texto(documento, "Reporte de evaluaciones antifraude", 23, true, ReportePresentacion.TINTA, 6);
            texto(documento, ReportePresentacion.periodo(filas), 9, false, ReportePresentacion.GRIS, 3);
            texto(documento, "Emitido: " + ReportePresentacion.emision(), 9, false, ReportePresentacion.GRIS, 16);
            BigDecimal monto = filas.stream().map(EvaluacionResponse::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            double promedio = filas.stream().mapToInt(EvaluacionResponse::puntaje).average().orElse(0);
            PdfPTable indicadores = new PdfPTable(4); indicadores.setWidthPercentage(100); indicadores.setSpacingAfter(12);
            String[] etiquetas = {"EVALUACIONES", "MONTO EVALUADO (PEN)", "PUNTAJE PROMEDIO", "ALTO + CRÍTICO"};
            String[] valores = {String.valueOf(filas.size()), ReportePresentacion.moneda(monto), filas.isEmpty() ? "Sin datos" : String.format(Locale.US, "%.1f / 100", promedio), String.valueOf(ReportePresentacion.cantidad(filas, "alto") + ReportePresentacion.cantidad(filas, "critico"))};
            for (int i = 0; i < etiquetas.length; i++) {
                PdfPCell celda = new PdfPCell(); celda.setBorder(Rectangle.NO_BORDER); celda.setPadding(12); celda.setBackgroundColor(ReportePresentacion.FONDO);
                celda.addElement(new Paragraph(etiquetas[i], fuente(8, true, ReportePresentacion.GRIS)));
                celda.addElement(new Paragraph(valores[i], fuente(i == 1 ? 16 : 19, true, ReportePresentacion.TINTA)));
                indicadores.addCell(celda);
            }
            documento.add(indicadores);
            texto(documento, ReportePresentacion.distribucion(filas), 9, false, ReportePresentacion.GRIS, 14);
            texto(documento, "Detalle de evaluaciones", 12, true, ReportePresentacion.TINTA, 8);
            PdfPTable tabla = new PdfPTable(new float[] {11, 12, 24, 14, 12, 11, 7, 8, 10, 16});
            tabla.setWidthPercentage(100); tabla.setHeaderRows(1); tabla.setSplitLate(false);
            for (String columna : ReportePresentacion.COLUMNAS) tabla.addCell(celda(columna, true, Color.WHITE, ReportePresentacion.TINTA, Element.ALIGN_LEFT));
            int indice = 0;
            for (var fila : filas) {
                Color fondo = indice++ % 2 == 0 ? Color.WHITE : ReportePresentacion.FONDO;
                String[] valoresFila = {fila.id(), fila.fecha(), fila.clienteNombre(), ReportePresentacion.moneda(fila.monto()), fila.categoria(), fila.pais(), String.format(Locale.ROOT, "%02d:00", fila.hora()), String.valueOf(fila.puntaje()), ReportePresentacion.nivel(fila.nivel()), fila.estado()};
                for (int columna = 0; columna < valoresFila.length; columna++) {
                    boolean riesgo = columna == 7 || columna == 8;
                    tabla.addCell(celda(valoresFila[columna], riesgo, riesgo ? ReportePresentacion.color(fila.nivel()) : ReportePresentacion.TINTA, fondo, columna == 3 || columna == 7 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
                }
            }
            if (filas.isEmpty()) {
                PdfPCell vacia = celda("No hay evaluaciones para los filtros seleccionados.", false, ReportePresentacion.GRIS, Color.WHITE, Element.ALIGN_CENTER);
                vacia.setColspan(10); vacia.setPadding(22); tabla.addCell(vacia);
            }
            documento.add(tabla);
        } catch (Exception ex) { throw new IllegalStateException("No se pudo generar el reporte PDF", ex); }
        finally { if (documento.isOpen()) documento.close(); }
        return salida.toByteArray();
    }
    private static Font fuente(float tamano, boolean negrita, Color color) {
        return FontFactory.getFont(negrita ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, tamano, color);
    }
    private static void texto(Document documento, String texto, float tamano, boolean negrita, Color color, float despues) throws DocumentException {
        Paragraph parrafo = new Paragraph(texto, fuente(tamano, negrita, color)); parrafo.setSpacingAfter(despues); documento.add(parrafo);
    }
    private static PdfPCell celda(String texto, boolean negrita, Color color, Color fondo, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente(8, negrita, color)));
        celda.setPadding(6); celda.setBorder(Rectangle.BOTTOM); celda.setBorderColor(new Color(228, 232, 238)); celda.setBorderWidth(0.4f);
        celda.setBackgroundColor(fondo); celda.setHorizontalAlignment(alineacion); celda.setVerticalAlignment(Element.ALIGN_MIDDLE); return celda;
    }
}
