package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.*;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import com.lambdashield.fraude.support.SesionesPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstadoReglasApiMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ReglaFraudeRepository reglas;
    @Autowired CatalogoReglasService catalogo;
    @Autowired ReglaConsultaService consultas;
    @Autowired com.lambdashield.fraude.repository.AuditoriaRepository auditoria;

    private org.springframework.test.web.servlet.ResultActions cambiar(String codigo, boolean activa) throws Exception {
        return mvc.perform(patch("/api/v1/rule/{codigo}/estado", codigo).session(SesionesPrueba.crear(auth, "ADMIN"))
                .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":" + activa + "}"));
    }

    private void estado(boolean activa) throws Exception {
        mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":" + activa + "}"))
                .andExpect(status().isNoContent());
    }

    @Test void desactivarYReactivarPersisteYExcluyeDelPuntaje() throws Exception {
        var transaccion = new TransaccionEvaluable(10, 12, "local", "nuevo", 0, "recurrente", "conocida");
        java.util.function.Supplier<java.util.Optional<FactorEvaluado>> dispositivo = () -> catalogo.evaluar(transaccion, 100).stream()
                .map(CatalogoReglasService.FactorConfigurado::factor).filter(f -> f.id().equals("dispositivo")).findFirst();
        var original = dispositivo.get().orElseThrow();
        estado(false);
        reglas.flush();
        assertThat(reglas.findByCodigoRegla("RF002").orElseThrow().isEsActiva()).isFalse();
        assertThat(dispositivo.get()).isEmpty();
        assertThat(consultas.listar()).noneMatch(r -> r.codigo().equals("RF002"));
        assertThat(consultas.listarTodas()).anyMatch(r -> r.codigo().equals("RF002") && !r.activa());
        estado(false);
        estado(true);
        assertThat(dispositivo.get().orElseThrow().puntaje()).isEqualTo(original.puntaje());
    }

    @Test void soloAdminConCsrfPuedeCambiarEstado() throws Exception {
        for (String rol : List.of("ANALISTA", "CLIENTE")) {
            mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, rol))
                    .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":false}"))
                    .andExpect(SesionesPrueba.alLogin());
        }
        mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                .contentType("application/json").content("{\"activa\":false}"))
                .andExpect(SesionesPrueba.alLogin());
        assertThat(reglas.findByCodigoRegla("RF002").orElseThrow().isEsActiva()).isTrue();
    }

    @Test void estadoObligatorioYReglaInexistente() throws Exception {
        for (String body : List.of("{}", "{\"activa\":null}")) {
            mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                    .with(SesionesPrueba.csrf()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/rule/NO_EXISTE/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test void campoDesarrolladoresRenderizaEscenariosYControlesActuales() throws Exception {
        mvc.perform(get("/simulador").session(SesionesPrueba.crear(auth, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Campo para desarrolladores")))
                .andExpect(content().string(containsString("id=\"escenarioSimulador\"")))
                .andExpect(content().string(containsString("id=\"estadoTrafico\"")));
    }

    @Test void inactivasSiguenVisiblesYControlesSoloParaAdmin() throws Exception {
        estado(false);
        mvc.perform(get("/reglas").session(SesionesPrueba.crear(auth, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-cambiar-regla")))
                .andExpect(content().string(containsString("Inactiva")))
                .andExpect(content().string(containsString("data-codigo=\"RF002\"")))
                .andExpect(content().string(containsString("id=\"tablaReglas\"")))
                .andExpect(content().string(containsString("id=\"buscarReglas\"")));
        mvc.perform(get("/reglas").session(SesionesPrueba.crear(auth, "ANALISTA")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("data-cambiar-regla"))))
                .andExpect(content().string(containsString("Inactiva")));
    }

    @Test void pesoInvalidoNoSeActivaNiImpideVerElCatalogo() throws Exception {
        estado(false);
        reglas.findByCodigoRegla("RF002").orElseThrow().setPeso(new java.math.BigDecimal("12.50"));
        reglas.flush();
        cambiar("RF002", true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value(containsString("entero entre 0 y 100")));
        assertThat(reglas.findByCodigoRegla("RF002").orElseThrow().isEsActiva()).isFalse();
        mvc.perform(get("/reglas").session(SesionesPrueba.crear(auth, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Peso inválido")));
    }

    @Test void noSePuedeDesactivarLaUltimaReglaActiva() throws Exception {
        var activas = reglas.findByEsActivaTrueOrderByCodigoReglaAsc();
        for (var regla : activas.subList(0, activas.size() - 1)) cambiar(regla.getCodigoRegla(), false).andExpect(status().isNoContent());
        String ultima = activas.getLast().getCodigoRegla();
        cambiar(ultima, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("Debe quedar al menos una regla activa en el motor."));
        assertThat(reglas.findByCodigoRegla(ultima).orElseThrow().isEsActiva()).isTrue();
    }

    @Test void cadaCambioRealQuedaAuditadoConSuAutor() throws Exception {
        estado(false);
        estado(false);
        estado(true);
        var eventos = auditoria.findByEntidadAndIdEntidadOrderByIdAuditoriaAsc("regla_fraude", "RF002");
        assertThat(eventos).extracting(e -> e.getAccion()).containsExactly("DESACTIVAR", "ACTIVAR");
        assertThat(eventos).allMatch(e -> e.getUsuario() != null && e.getUsuario().getNombreUsuario().equals("admin"));
    }
}
