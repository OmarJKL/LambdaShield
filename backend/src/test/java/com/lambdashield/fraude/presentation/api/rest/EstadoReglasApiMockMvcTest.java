package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.*;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
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

    private void estado(boolean activa) throws Exception {
        mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":" + activa + "}"))
                .andExpect(status().isNoContent());
    }

    @Test void desactivarYReactivarPersisteYExcluyeDelPuntaje() throws Exception {
        var factores = List.of(new FactorEvaluado("dispositivo", "Dispositivo", 15, 15, "Nuevo"));
        var original = catalogo.aplicar(factores);
        assertThat(original).hasSize(1);
        estado(false);
        reglas.flush();
        assertThat(reglas.findByCodigoRegla("RF002").orElseThrow().isEsActiva()).isFalse();
        assertThat(catalogo.aplicar(factores)).isEmpty();
        assertThat(consultas.listar()).noneMatch(r -> r.codigo().equals("RF002"));
        assertThat(consultas.listarTodas()).anyMatch(r -> r.codigo().equals("RF002") && !r.activa());
        estado(false);
        estado(true);
        assertThat(catalogo.aplicar(factores).getFirst().factor().puntaje()).isEqualTo(original.getFirst().factor().puntaje());
    }

    @Test void soloAdminConCsrfPuedeCambiarEstado() throws Exception {
        for (String rol : List.of("ANALISTA", "CLIENTE")) {
            mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, rol))
                    .with(SesionesPrueba.csrf()).contentType("application/json").content("{\"activa\":false}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/v1/rule/RF002/estado").session(SesionesPrueba.crear(auth, "ADMIN"))
                .contentType("application/json").content("{\"activa\":false}"))
                .andExpect(status().isForbidden());
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

    @Test void inactivasSiguenVisiblesYControlesSoloParaAdmin() throws Exception {
        estado(false);
        mvc.perform(get("/reglas").session(SesionesPrueba.crear(auth, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-cambiar-regla")))
                .andExpect(content().string(containsString("Inactiva")))
                .andExpect(content().string(containsString("data-codigo=\"RF002\"")));
        mvc.perform(get("/reglas").session(SesionesPrueba.crear(auth, "ANALISTA")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("data-cambiar-regla"))))
                .andExpect(content().string(containsString("Inactiva")));
    }
}
