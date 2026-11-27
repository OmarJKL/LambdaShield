package com.lambdashield.fraude.presentation.export;

import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

/** PDF con la misma tabla plana del CSV/Excel: un título, la tabla y el número de página. */
@Component
public class ReportePdfExporter {
    private static final Color BORDE = new Color(210, 214, 220);
    private static final Color CABECERA = new Color(235, 238, 242);

    public byte[] exportar(List<EvaluacionResponse> filas) {
        var salida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 34);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Página " + w.getPageNumber(), fuente(8, false)), d.right(), 18, 0);
                }
            });
            documento.addTitle("Evaluaciones antifraude");
            documento.open();

            Paragraph titulo = new Paragraph(
                    "Evaluaciones antifraude (" + filas.size() + " registros)", fuente(12, true));
            titulo.setSpacingAfter(8);
            documento.add(titulo);

            PdfPTable tabla = new PdfPTable(new float[] {10, 9, 6, 10, 26, 11, 13, 11, 7, 8, 17});
            tabla.setWidthPercentage(100);
            tabla.setHeaderRows(1);
            for (String columna : ReportePresentacion.COLUMNAS) {
                PdfPCell c = celda(columna, true, Element.ALIGN_LEFT);
                c.setBackgroundColor(CABECERA);
                tabla.addCell(c);
            }
            for (var e : filas) {
                String[] valores = {
                        ReportePresentacion.texto(e.id()), ReportePresentacion.fecha(e), ReportePresentacion.hora(e),
                        ReportePresentacion.texto(e.clienteCodigo()), ReportePresentacion.texto(e.clienteNombre()),
                        String.format(Locale.US, "%,.2f", e.monto()), ReportePresentacion.texto(e.categoria()),
                        ReportePresentacion.texto(e.pais()), String.valueOf(e.puntaje()),
                        ReportePresentacion.nivel(e.nivel()), ReportePresentacion.texto(e.estado())};
                for (int i = 0; i < valores.length; i++) {
                    tabla.addCell(celda(valores[i], false,
                            i == 5 || i == 8 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
                }
            }
            if (filas.isEmpty()) {
                PdfPCell vacia = celda("No hay evaluaciones para los filtros seleccionados.", false, Element.ALIGN_CENTER);
                vacia.setColspan(ReportePresentacion.COLUMNAS.length);
                vacia.setPadding(12);
                tabla.addCell(vacia);
            }
            documento.add(tabla);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el reporte PDF", ex);
        } finally {
            if (documento.isOpen()) documento.close();
        }
        return salida.toByteArray();
    }

    private static Font fuente(float tamano, boolean negrita) {
        return FontFactory.getFont(negrita ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, tamano, Color.BLACK);
    }

    private static PdfPCell celda(String texto, boolean negrita, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente(7.5f, negrita)));
        celda.setPadding(4);
        celda.setBorderColor(BORDE);
        celda.setBorderWidth(0.4f);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }
}
