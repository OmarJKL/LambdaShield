package com.lambdashield.fraude.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:produccion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProduccionSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired Environment entorno;

    @Test
    void httpRedirigeAHttpsInclusoEnUnaRutaPublica() throws Exception {
        mvc.perform(get("/login")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://localhost/login"));
    }

    @Test
    void httpsPermiteElLoginYActivaHsts() throws Exception {
        mvc.perform(get("https://localhost/login").secure(true)).andExpect(status().isOk())
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void perfilExigeCookieSeguraYDeshabilitaFixturesYSql() {
        assertThat(entorno.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(entorno.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(entorno.getProperty("app.demo.enabled", Boolean.class)).isFalse();
        assertThat(entorno.getProperty("app.demo.dataset.enabled", Boolean.class)).isFalse();
    }
}
