package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.AuthService;

import com.lambdashield.fraude.support.SesionesPrueba;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exportación de reportes en CSV, Excel y PDF, con filtros por fecha, nivel y
 * cliente, y validación del formato solicitado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReporteApiMockMvcTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession analista() {
        return SesionesPrueba.crear(authService, "ANALISTA");
    }

    @ParameterizedTest
    @CsvSource({
            "csv, text/csv, reporte_transacciones_lambda.csv, ID",
            "xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, reporte_transacciones_lambda.xlsx, PK",
            "pdf, application/pdf, reporte_transacciones_lambda.pdf, %PDF"})
    void exportaCadaFormatoConTipoNombreYFirma(String formato, String tipo, String archivo, String firma) throws Exception {
        byte[] cuerpo = mockMvc.perform(get("/api/v1/report/export?formato=" + formato).session(analista()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(tipo))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(archivo)))
                .andReturn().getResponse().getContentAsByteArray();

        // CSV empieza con su encabezado (ID), XLSX es un ZIP (PK) y PDF declara su versión.
        assertThat(new String(cuerpo, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith(firma);
        if ("csv".equals(formato)) {
            assertThat(new String(cuerpo, java.nio.charset.StandardCharsets.UTF_8).lines().count()).isGreaterThan(1);
        }
    }

    @Test
    void filtroPorNivelSoloIncluyeEseNivel() throws Exception {
        String csv = new String(
                mockMvc.perform(get("/api/v1/report/export?formato=csv&nivel=critico").session(analista()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv.lines().skip(1).allMatch(linea -> linea.contains("\"CRITICO\""))).isTrue();
    }

    @Test
    void filtroPorClienteSoloIncluyeEseCliente() throws Exception {
        String csv = new String(
                mockMvc.perform(get("/api/v1/report/export?formato=csv&cliente=C-001&nivel=bajo").session(analista()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv.lines().skip(1).toList()).isNotEmpty()
                .allMatch(linea -> linea.contains("María Fernández") && linea.contains("\"BAJO\""));
    }

    @Test
    void formatoONivelInvalidosDevuelven400() throws Exception {
        mockMvc.perform(get("/api/v1/report/export?formato=docx").session(analista()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/report/export?formato=csv&nivel=extremo").session(analista()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteNoPuedeExportarReportes() throws Exception {
        MockHttpSession cliente = SesionesPrueba.crear(authService, "CLIENTE");

        mockMvc.perform(get("/api/v1/report/export?formato=csv").session(cliente))
                .andExpect(SesionesPrueba.alLogin());
    }
}
