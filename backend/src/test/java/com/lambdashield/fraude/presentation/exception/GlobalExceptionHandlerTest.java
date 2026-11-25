package com.lambdashield.fraude.presentation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorConFallo())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Controller
    static class ControladorConFallo {
        @GetMapping({"/pagina", "/api/fallo"})
        String fallo() { throw new IllegalStateException("Detalle SQL privado de prueba"); }
    }

    @Test
    void paginaResponde500SinExponerElMensajeDeLaExcepcion() throws Exception {
        mvc.perform(get("/pagina")).andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("error", "Error interno del servidor"));
    }

    @Test
    void apiResponde500ConUnMensajeGenerico() throws Exception {
        mvc.perform(get("/api/fallo")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errores[0]").value("Error interno del servidor"));
    }
}
