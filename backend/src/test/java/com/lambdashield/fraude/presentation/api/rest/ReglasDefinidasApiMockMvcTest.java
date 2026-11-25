package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.application.service.EvaluacionConsultaService;
import com.lambdashield.fraude.application.service.EvaluacionTransaccionService;
import com.lambdashield.fraude.application.service.ReglaConsultaService;
import com.lambdashield.fraude.repository.*;
import com.lambdashield.fraude.support.SesionesPrueba;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reglas definidas desde el formulario: alta, reparto porcentual, ejecución y pruebas sin guardar. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReglasDefinidasApiMockMvcTest {
    private static final String DEFINICION = """
            {"nombre":"Dispositivo nuevo definido","objetivo":"","combinacion":"TODAS","puntos":25,
             "condiciones":[{"campo":"DISPOSITIVO","operador":"IGUAL","valor":"nuevo"}]}
            """;
    private static final String ESCENARIO = """
            {"cliente":"C-001","monto":10,"categoria":"categoria_de_prueba","hora":12,"pais":"local",
             "dispositivo":"nuevo","operacionesUltimaHora":0,"beneficiario":"recurrente","ip":"conocida"}
            """;

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ReglaFraudeRepository reglas;
    @Autowired ReglaConsultaService consultas;
    @Autowired AuditoriaRepository auditoria;
    @Autowired EvaluacionTransaccionService operaciones;
    @Autowired EvaluacionConsultaService historial;
    @Autowired TransaccionRepository transacciones;
    @Autowired EvaluacionFraudeRepository evaluaciones;
    @Autowired BeneficiarioRepository beneficiarios;
    @Autowired DispositivoRepository dispositivos;
    @Autowired TipoTransaccionRepository tipos;
    @Autowired CuentaRepository cuentas;
    @Autowired ClienteRepository clientes;

    private Long idC001() {
        return clientes.findByCodigoCliente("C-001").orElseThrow().getIdCliente();
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/rule").session(SesionesPrueba.crear(auth, "ADMIN")).with(SesionesPrueba.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void estado(String codigo, boolean activa) throws Exception {
        mvc.perform(patch("/api/v1/rule/{codigo}/estado", codigo).session(SesionesPrueba.crear(auth, "ADMIN"))
                        .with(SesionesPrueba.csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"activa\":" + activa + "}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions simular(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/rule/simulation").session(SesionesPrueba.crear(auth, "ADMIN")).with(SesionesPrueba.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private BigDecimal participacion(String codigo) {
        return consultas.listarTodas().stream().filter(r -> r.codigo().equals(codigo)).findFirst().orElseThrow().participacion();
    }

    private EvaluarTransaccionCommand operacion(String dispositivo) {
        return new EvaluarTransaccionCommand("C-001", new BigDecimal("1.00"), "servicios", 12, "regional",
                dispositivo, 0, "recurrente", "conocida", null);
    }

    @Test
    void guardaLaDefinicionComoReglaInactivaConCodigoConsecutivoYAuditoria() throws Exception {
        crear(DEFINICION).andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("RC001")).andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.personalizada").value(true)).andExpect(jsonPath("$.pesoMaximo").value(25))
                .andExpect(jsonPath("$.descripcion").value("Se activa cuando se cumplen todas: Dispositivo = nuevo"));
        crear(DEFINICION.replace("Dispositivo nuevo definido", "Otra")).andExpect(jsonPath("$.codigo").value("RC002"));
        var regla = reglas.findByCodigoRegla("RC001").orElseThrow();
        assertThat(regla.isEsActiva()).isFalse();
        assertThat(regla.getParametrosJson()).contains("\"DISPOSITIVO\"");
        assertThat(auditoria.findByEntidadAndIdEntidadOrderByIdAuditoriaAsc("regla_fraude", "RC001"))
                .singleElement().satisfies(a -> assertThat(a.getAccion()).isEqualTo("CREAR"));
    }

    @Test
    void altaValidaEnElServidorYSoloLaHaceAdminConCsrf() throws Exception {
        crear(DEFINICION.replace("\"puntos\":25", "\"puntos\":0")).andExpect(status().isBadRequest());
        crear(DEFINICION.replace("\"valor\":\"nuevo\"", "\"valor\":\"x; DROP TABLE\"")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rule").session(SesionesPrueba.crear(auth, "ANALISTA")).with(SesionesPrueba.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(DEFINICION)).andExpect(SesionesPrueba.alLogin());
        mvc.perform(post("/api/v1/rule").session(SesionesPrueba.crear(auth, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(DEFINICION)).andExpect(SesionesPrueba.alLogin());
        assertThat(reglas.findByCodigoRegla("RC001")).isEmpty();
    }

    @Test
    void activarDesactivarOAgregarReglasReparteDeNuevoElPorcentaje() throws Exception {
        // Ocho reglas RF con pesos 25+15+10+30+10+25+40+20 = 175.
        assertThat(participacion("RF004")).isEqualByComparingTo("17.1");
        crear(DEFINICION).andExpect(status().isCreated());
        assertThat(participacion("RC001")).isNull();
        estado("RC001", true);
        assertThat(participacion("RF004")).isEqualByComparingTo("15.0");
        assertThat(participacion("RC001")).isEqualByComparingTo("12.5");
        estado("RF007", false);
        assertThat(participacion("RF004")).isEqualByComparingTo("18.8");
        assertThat(consultas.listar().stream().map(r -> r.participacion()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isBetween(new BigDecimal("99.9"), new BigDecimal("100.1"));
    }

    @Test
    void elMotorEjecutaLaReglaDefinidaYElHistorialConservaSusMaximos() throws Exception {
        var anterior = operaciones.crearYEvaluar(operacion("nuevo"));
        crear(DEFINICION).andExpect(status().isCreated());
        estado("RC001", true);

        var conRegla = operaciones.crearYEvaluar(operacion("nuevo"));
        var definida = conRegla.factores().stream().filter(f -> f.id().equals("rc001")).findFirst().orElseThrow();
        assertThat(definida.puntaje()).isEqualTo(13);
        assertThat(definida.max()).isEqualTo(13);
        assertThat(conRegla.factores().stream().filter(f -> f.id().equals("ubicacion")).findFirst().orElseThrow().max()).isEqualTo(15);
        assertThat(operaciones.crearYEvaluar(operacion("reconocido")).factores().stream()
                .filter(f -> f.id().equals("rc001")).findFirst().orElseThrow().puntaje()).isZero();

        estado("RC001", false);
        assertThat(operaciones.crearYEvaluar(operacion("nuevo")).factores()).noneMatch(f -> f.id().equals("rc001"));
        var guardada = historial.listar(true).stream().filter(e -> e.idInterno().equals(anterior.idInterno())).findFirst().orElseThrow();
        assertThat(guardada.factores().stream().filter(f -> f.id().equals("ubicacion")).findFirst().orElseThrow().max()).isEqualTo(17);
        assertThat(historial.listar(true).stream().filter(e -> e.idInterno().equals(conRegla.idInterno())).findFirst().orElseThrow()
                .factores()).anyMatch(f -> f.id().equals("rc001") && f.puntaje() == 13);
    }

    @Test
    void probarSinGuardarComparaCandidatasYBorradorSinPersistirNada() throws Exception {
        crear(DEFINICION).andExpect(status().isCreated());
        long tx = transacciones.count(), ev = evaluaciones.count(), ben = beneficiarios.count(), dis = dispositivos.count();
        BigDecimal saldo = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(idC001()).orElseThrow().getSaldoDisponible();
        String borrador = DEFINICION.replace("Dispositivo nuevo definido", "Borrador de prueba").replace("\"puntos\":25", "\"puntos\":50");

        simular("{\"escenarios\":[" + ESCENARIO + "],\"candidatas\":[\"RC001\"],\"borrador\":" + borrador + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].vigente.factores[?(@.id == 'rc001')]").isEmpty())
                .andExpect(jsonPath("$.resultados[0].conCandidatas.factores[?(@.id == 'rc001')].puntaje").value(contains(10)))
                .andExpect(jsonPath("$.resultados[0].conCandidatas.factores[?(@.id == 'borrador')].puntaje").value(contains(20)))
                .andExpect(jsonPath("$.resultados[0].conCandidatas.puntaje").value(greaterThan(0)))
                .andExpect(jsonPath("$.resultados[0].fondosSuficientes").value(true));

        assertThat(List.of(transacciones.count(), evaluaciones.count(), beneficiarios.count(), dispositivos.count()))
                .containsExactly(tx, ev, ben, dis);
        assertThat(tipos.findByCodigoTipo("categoria_de_prueba")).isEmpty();
        assertThat(reglas.findByCodigoRegla("RC001").orElseThrow().isEsActiva()).isFalse();
        assertThat(cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(idC001()).orElseThrow().getSaldoDisponible()).isEqualByComparingTo(saldo);
    }

    @Test
    void laPruebaValidaCandidatasCantidadYPermisos() throws Exception {
        simular("{\"escenarios\":[" + ESCENARIO + "]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].conCandidatas").doesNotExist());
        simular("{\"escenarios\":[" + ESCENARIO + "],\"candidatas\":[\"NO_EXISTE\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value(containsString("NO_EXISTE")));
        simular("{\"escenarios\":[" + String.join(",", java.util.Collections.nCopies(101, ESCENARIO)) + "]}")
                .andExpect(status().isBadRequest());
        simular("{\"escenarios\":[" + ESCENARIO.replace("\"monto\":10", "\"monto\":-1") + "]}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rule/simulation").session(SesionesPrueba.crear(auth, "ANALISTA")).with(SesionesPrueba.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"escenarios\":[" + ESCENARIO + "]}")).andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void unaDefinicionDanadaNoSePuedeActivar() throws Exception {
        crear(DEFINICION).andExpect(status().isCreated());
        reglas.findByCodigoRegla("RC001").orElseThrow().setParametrosJson("{\"nombre\":\"\"}");
        reglas.flush();
        mvc.perform(patch("/api/v1/rule/RC001/estado").session(SesionesPrueba.crear(auth, "ADMIN")).with(SesionesPrueba.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("No se puede activar una regla sin implementación en el motor."));
    }
}
