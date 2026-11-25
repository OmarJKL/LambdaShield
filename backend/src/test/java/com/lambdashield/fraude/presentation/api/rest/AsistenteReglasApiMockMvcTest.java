package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.model.RevisionRegla;
import com.lambdashield.fraude.application.service.*;
import com.lambdashield.fraude.presentation.security.SessionValidationFilter;
import com.lambdashield.fraude.support.SesionesPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AsistenteReglasApiMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @MockitoBean AsistenteReglasPort proveedor;
    private static final String BODY = """
        {"nombre":"Monto alto","objetivo":"Revisar","combinacion":"TODAS","puntos":25,
        "condiciones":[{"campo":"MONTO","operador":"MAYOR","valor":"1000"}]}
        """;
    private MockHttpSession sesion(String nombre) {
        var user = auth.obtenerSesionActiva(nombre).orElseThrow();
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(SessionValidationFilter.authentication(user));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return session;
    }
    @Test void soloAdministradorPuedeRevisarYRequiereCsrf() throws Exception {
        mvc.perform(get("/api/v1/rule/assistant/status")).andExpect(SesionesPrueba.alLogin());
        for (String user : List.of("analista", "usuario")) {
            mvc.perform(get("/api/v1/rule/assistant/status").session(sesion(user))).andExpect(SesionesPrueba.alLogin());
            mvc.perform(post("/api/v1/rule/assistant/review").session(sesion(user)).with(SesionesPrueba.csrf())
                    .contentType("application/json").content(BODY)).andExpect(SesionesPrueba.alLogin());
        }
        mvc.perform(post("/api/v1/rule/assistant/review").session(sesion("admin"))
                .contentType("application/json").content(BODY)).andExpect(SesionesPrueba.alLogin());
        verifyNoInteractions(proveedor);
    }
    @Test void devuelveDefinicionOriginalYRevisionSeparada() throws Exception {
        when(proveedor.revisar(any())).thenReturn(new RevisionRegla("Interpretación", List.of(), List.of("Propuesta"), List.of("Caso 1", "Caso 2")));
        mvc.perform(post("/api/v1/rule/assistant/review").session(sesion("admin")).with(SesionesPrueba.csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.definicion.puntos").value(25)).andExpect(jsonPath("$.revision.sugerencias[0]").value("Propuesta"));
    }
    @Test void rechazaValoresYOperadoresIncompatiblesAntesDeConsumirTokens() throws Exception {
        for (String body : List.of(BODY.replace("25", "101"), BODY.replace("MONTO", "INVENTADO"),
                BODY.replace("MONTO", "DISPOSITIVO"), BODY.replace("1000", "-1"), BODY.replace("MONTO", "HORA"),
                BODY.replace("MONTO", "RATIO_PROMEDIO").replace("1000", "1000000.01"))) {
            mvc.perform(post("/api/v1/rule/assistant/review").session(sesion("admin")).with(SesionesPrueba.csrf())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(proveedor);
    }
    @Test void objetivoEsOpcionalTalComoIndicaElFormulario() throws Exception {
        when(proveedor.revisar(any())).thenReturn(new RevisionRegla("Interpretación", List.of(), List.of(), List.of("Caso 1", "Caso 2")));
        mvc.perform(post("/api/v1/rule/assistant/review").session(sesion("admin")).with(SesionesPrueba.csrf())
                .contentType("application/json").content(BODY.replace("\"objetivo\":\"Revisar\",", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.definicion.objetivo").value(""));
    }
    @Test void configuracionPendienteSeComunicaSinExponerSecretos() throws Exception {
        mvc.perform(get("/api/v1/rule/assistant/status").session(sesion("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.apiKey").doesNotExist());
        when(proveedor.revisar(any())).thenThrow(new AsistenteReglasException("Configuración pendiente", true));
        mvc.perform(post("/api/v1/rule/assistant/review").session(sesion("admin")).with(SesionesPrueba.csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isServiceUnavailable());
    }
    @Test void formularioIaSoloSeMuestraAlAdministrador() throws Exception {
        mvc.perform(get("/reglas").session(sesion("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"asistenteReglas\"")));
        mvc.perform(get("/reglas").session(sesion("analista"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"asistenteReglas\""))));
    }
}
