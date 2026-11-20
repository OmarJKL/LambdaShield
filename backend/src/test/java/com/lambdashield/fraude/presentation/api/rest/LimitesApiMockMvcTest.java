package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.support.SesionesPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:limites;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "security.rate-limit.api-per-minute=2", "security.rate-limit.writes-per-minute=1",
        "security.rate-limit.login-per-minute=2"})
@AutoConfigureMockMvc
@Transactional
class LimitesApiMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;

    @Test
    void apiResponde429AlSuperarLaVentana() throws Exception {
        var sesion = SesionesPrueba.crear(auth, "ADMIN");
        for (int i = 0; i < 2; i++) mvc.perform(get("/api/v1/rule").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/rule").session(sesion)).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
    @Test
    void escriturasTienenUnLimiteSeparado() throws Exception {
        var sesion = SesionesPrueba.crear(auth, "CLIENTE");
        String operacion = """
                {"cliente":"C-001","monto":1,"categoria":"servicios","hora":12,"pais":"local",
                "dispositivo":"reconocido","operacionesUltimaHora":0,"beneficiario":"recurrente","ip":"conocida"}
                """;
        mvc.perform(post("/api/v1/evaluation").session(sesion).with(SesionesPrueba.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(operacion)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/evaluation").session(sesion).with(SesionesPrueba.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(operacion)).andExpect(status().isTooManyRequests());
    }
    @Test
    void intentosDeLoginSeLimitanPorIpAunqueCambieLaSesion() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(post("/login").session(new MockHttpSession()).with(SesionesPrueba.csrf())
                .param("usuario", "no-existe").param("clave", "incorrecta")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").session(new MockHttpSession()).with(SesionesPrueba.csrf())
                        .param("usuario", "otro-no-existe").param("clave", "incorrecta"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }
}
