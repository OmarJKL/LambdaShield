package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.AuthService;

import com.lambdashield.fraude.support.SesionesPrueba;

import com.lambdashield.fraude.repository.EvaluacionFraudeRepository;
import com.lambdashield.fraude.presentation.dto.response.EvaluacionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Pruebas MockMvc de autenticación, autorización por rol, baja lógica con
 * PATCH, reactivación y aislamiento de datos del portal del cliente.
 *
 * El contexto completo usa H2 en modo PostgreSQL y el DataInitializer real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadApiMockMvcTest {

    private static final String JSON_VALIDO = """
            {
              "cliente": "C-001",
              "monto": 150.0,
              "categoria": "servicios",
              "hora": 12,
              "pais": "local",
              "dispositivo": "reconocido",
              "operacionesUltimaHora": 1,
              "beneficiario": "recurrente",
              "ip": "conocida"
            }
            """;

    @Autowired
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvaluacionFraudeRepository evaluacionFraudeRepository;

    private MockHttpSession sesion(String rol) {
        return SesionesPrueba.crear(authService, rol);
    }

    private Long primeraActiva() {
        return evaluacionFraudeRepository.findByEsActivaTrueOrderByFechaEvaluacionDesc().getFirst().getIdEvaluacion();
    }

    private void desactivar(Long id) throws Exception {
        mockMvc.perform(patch("/api/v1/evaluation/{id}", id).with(SesionesPrueba.csrf()).session(sesion("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void apiSinSesionRedirigeAlLoginSinMensaje() throws Exception {
        mockMvc.perform(get("/api/v1/rule"))
                .andExpect(SesionesPrueba.alLogin());

        mockMvc.perform(get("/api/v1/evaluation"))
                .andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void clienteNoPuedeAccederAEndpointsDeAdministracion() throws Exception {
        mockMvc.perform(get("/api/v1/metric").session(sesion("CLIENTE")))
                .andExpect(SesionesPrueba.alLogin());

        mockMvc.perform(get("/api/v1/rule").session(sesion("CLIENTE")))
                .andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void analistaLeePeroNiAnalistaNiClienteDesactivanOReactivan() throws Exception {
        mockMvc.perform(get("/api/v1/rule").session(sesion("ANALISTA")))
                .andExpect(status().isOk());

        Long id = primeraActiva();
        mockMvc.perform(patch("/api/v1/evaluation/{id}", id).with(SesionesPrueba.csrf()).session(sesion("ANALISTA")))
                .andExpect(SesionesPrueba.alLogin());

        desactivar(id);
        for (String rol : List.of("ANALISTA", "CLIENTE")) {
            mockMvc.perform(patch("/api/v1/evaluation/{id}/activar", id).with(SesionesPrueba.csrf()).session(sesion(rol)))
                    .andExpect(SesionesPrueba.alLogin());
        }
    }

    @Test
    void clienteInexistenteDevuelve404() throws Exception {
        String inexistente = JSON_VALIDO.replace("\"C-001\"", "\"C-999\"");

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf())
                        .session(sesion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores[0]").value("Cliente no encontrado: C-999"));
    }

    @Test
    void operacionSoloMuestraTransaccionesDelClienteAutenticado() throws Exception {
        MvcResult result = mockMvc.perform(get("/operacion").session(sesion("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(view().name("cliente/operacion"))
                .andReturn();

        assertThat(transaccionesDelModelo(result)).isNotEmpty().allMatch(t -> "C-001".equals(t.clienteCodigo()));
        // El HTML renderizado tampoco incluye operaciones semilla de C-002 ni C-003.
        assertThat(result.getResponse().getContentAsString())
                .contains("TR-00001", "TR-00002", "TR-00005")
                .doesNotContain("TR-00003", "TR-00004", "TR-00006", "TR-00007", "TR-00008", "TR-00009", "TR-00010");
    }

    @Test
    void operacionRecuperaCodigoClienteDesdeIdentidadAutenticada() throws Exception {
        MockHttpSession clienteSinCodigo = sesion("CLIENTE");
        clienteSinCodigo.removeAttribute("codigoCliente");

        MvcResult result = mockMvc.perform(get("/operacion").session(clienteSinCodigo))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(transaccionesDelModelo(result)).isNotEmpty().allMatch(t -> "C-001".equals(t.clienteCodigo()));
    }

    @Test
    void adminReactivaEvaluacionDesactivada() throws Exception {
        Long id = primeraActiva();
        desactivar(id);

        mockMvc.perform(patch("/api/v1/evaluation/{id}/activar", id).with(SesionesPrueba.csrf()).session(sesion("ADMIN")))
                .andExpect(status().isNoContent());

        var restaurada = evaluacionFraudeRepository.findById(id).orElseThrow();
        assertThat(restaurada.isEsActiva()).isTrue();
        assertThat(restaurada.getFechaDesactivacion()).isNull();
    }

    @Test
    void reactivarEvaluacionInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/v1/evaluation/{id}/activar", 999999).with(SesionesPrueba.csrf()).session(sesion("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void reactivarEvaluacionYaActivaDevuelve400() throws Exception {
        Long id = primeraActiva();

        mockMvc.perform(patch("/api/v1/evaluation/{id}/activar", id).with(SesionesPrueba.csrf()).session(sesion("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("La evaluación ya está activa: " + id));
    }

    @Test
    void bajaLogicaConservaLaFilaYSoloSeListaConActivasFalse() throws Exception {
        Long id = primeraActiva();
        desactivar(id);

        assertThat(evaluacionFraudeRepository.findById(id)).get().extracting(e -> e.isEsActiva()).isEqualTo(false);

        mockMvc.perform(get("/api/v1/evaluation").session(sesion("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idInterno == " + id + ")]").isEmpty());

        mockMvc.perform(get("/api/v1/evaluation?activas=false").session(sesion("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idInterno == " + id + ")].activa")
                        .value(org.hamcrest.Matchers.contains(false)));
    }

    @SuppressWarnings("unchecked")
    private static List<EvaluacionResponse> transaccionesDelModelo(MvcResult result) {
        return (List<EvaluacionResponse>) result.getModelAndView()
                .getModel()
                .get("transacciones");
    }
}
