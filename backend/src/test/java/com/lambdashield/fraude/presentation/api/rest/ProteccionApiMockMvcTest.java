package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.application.service.UsuarioAdminService;
import com.lambdashield.fraude.domain.entity.UsuarioRol;
import com.lambdashield.fraude.domain.enums.EstadoUsuario;
import com.lambdashield.fraude.repository.*;
import com.lambdashield.fraude.support.SesionesPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProteccionApiMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UsuarioAdminService usuarios;
    @Autowired UsuarioSistemaRepository cuentas;
    @Autowired UsuarioRolRepository asignaciones;
    @Autowired RolRepository roles;
    @Autowired ClienteRepository clientes;
    @Autowired EvaluacionFraudeRepository evaluaciones;

    private static final String OPERACION = """
            {"cliente":"C-002","monto":1.01,"categoria":"servicios","hora":12,"pais":"local",
            "dispositivo":"reconocido","operacionesUltimaHora":0,"beneficiario":"recurrente","ip":"conocida"}
            """;
    private MockHttpSession admin() { return SesionesPrueba.crear(auth, "ADMIN"); }

    @Test
    void atributosDeSesionNoBastanParaSuplantarUnaIdentidad() throws Exception {
        MockHttpSession falsa = new MockHttpSession();
        falsa.setAttribute("usuario", "admin");
        falsa.setAttribute("rol", "ADMIN");
        mvc.perform(get("/api/v1/user").session(falsa)).andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void csrfAusenteOInvalidoImpideEscriturasSinCrearEvaluaciones() throws Exception {
        long antes = evaluaciones.count();
        mvc.perform(post("/api/v1/evaluation").session(admin()).contentType(MediaType.APPLICATION_JSON).content(OPERACION))
                .andExpect(SesionesPrueba.alLogin());
        mvc.perform(post("/api/v1/evaluation").session(admin()).header("X-CSRF-TOKEN", "falso")
                        .contentType(MediaType.APPLICATION_JSON).content(OPERACION))
                .andExpect(SesionesPrueba.alLogin());
        assertThat(evaluaciones.count()).isEqualTo(antes);
    }

    @Test
    void tokenDeOtraSesionNoSirve() throws Exception {
        var pagina = mvc.perform(get("/login")).andReturn();
        CsrfToken token = (CsrfToken) pagina.getRequest().getAttribute("_csrf");
        mvc.perform(post("/api/v1/evaluation").session(admin()).header(token.getHeaderName(), token.getToken())
                        .contentType(MediaType.APPLICATION_JSON).content(OPERACION))
                .andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void clienteNoPuedeCambiarSuVinculoMedianteBodyNiAtributosObsoletos() throws Exception {
        var sesion = SesionesPrueba.crear(auth, "CLIENTE");
        sesion.setAttribute("codigoCliente", "C-002");
        sesion.setAttribute("rol", "ADMIN");
        mvc.perform(post("/api/v1/evaluation").session(sesion).with(SesionesPrueba.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(OPERACION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteCodigo").value("C-001"));
        mvc.perform(get("/api/v1/user").session(sesion)).andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void usuarioBloqueadoPierdeAccesoConSesionAbierta() throws Exception {
        var sesion = admin();
        cuentas.findByNombreUsuario("admin").orElseThrow().setEstadoUsuario(EstadoUsuario.BLOQUEADO);
        mvc.perform(get("/api/v1/rule").session(sesion)).andExpect(SesionesPrueba.alLogin());
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void cambioDeClaveRevocaSesionAnterior() throws Exception {
        var sesion = admin();
        usuarios.cambiarClave(cuentas.findByNombreUsuario("admin").orElseThrow().getIdUsuario(), "ClaveNuevaSegura123");
        mvc.perform(get("/api/v1/user").session(sesion)).andExpect(SesionesPrueba.alLogin());
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void usuarioEliminadoPierdeSuSesionAbierta() throws Exception {
        var sesion = SesionesPrueba.crear(auth, "ANALISTA");
        var usuario = cuentas.findByNombreUsuario("analista").orElseThrow();
        asignaciones.deleteAll(asignaciones.findByUsuario_IdUsuario(usuario.getIdUsuario()));
        cuentas.delete(usuario);
        cuentas.flush();
        mvc.perform(get("/api/v1/rule").session(sesion)).andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void degradarRolSeAplicaEnLaSiguientePeticion() throws Exception {
        var sesion = admin();
        var usuario = cuentas.findByNombreUsuario("admin").orElseThrow();
        asignaciones.deleteAll(asignaciones.findByUsuario_IdUsuario(usuario.getIdUsuario()));
        asignaciones.flush();
        asignaciones.save(UsuarioRol.builder().usuario(usuario).rol(roles.findByCodigoRol("ANALISTA").orElseThrow())
                .fechaAsignacion(OffsetDateTime.now()).build());
        mvc.perform(get("/api/v1/metric").session(sesion)).andExpect(status().isOk());
        assertThat(sesion.getAttribute("rol")).isEqualTo("ANALISTA");
        // Una API sin permiso cierra la sesión: el login no devuelve a quien sigue autenticado a su inicio.
        mvc.perform(get("/api/v1/user").session(sesion)).andExpect(SesionesPrueba.alLogin());
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void clienteDesactivadoNoPuedeTransferir() throws Exception {
        var sesion = SesionesPrueba.crear(auth, "CLIENTE");
        clientes.findByCodigoCliente("C-001").orElseThrow().setEsActivo(false);
        mvc.perform(post("/api/v1/evaluation").session(sesion).with(SesionesPrueba.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(OPERACION))
                .andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void rutasNuevasYMetodosNoDeclaradosSeDenieganInclusoAlAdmin() throws Exception {
        mvc.perform(get("/api/v1/futura").session(admin())).andExpect(SesionesPrueba.alLogin());
        mvc.perform(get("/api/v1/user_extra").session(admin())).andExpect(SesionesPrueba.alLogin());
        mvc.perform(put("/api/v1/evaluation").session(admin()).with(SesionesPrueba.csrf())).andExpect(SesionesPrueba.alLogin());
        mvc.perform(delete("/api/v1/evaluation/1").session(admin()).with(SesionesPrueba.csrf())).andExpect(SesionesPrueba.alLogin());
    }

    @Test
    void firewallRechazaPuntoYComaParaEvitarRutasAmbiguas() throws Exception {
        mvc.perform(get("/api/v1/user;otro").session(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void respuestasPrivadasNoSeCacheanNiPermitenMarcosOExponenCors() throws Exception {
        mvc.perform(get("/api/v1/rule").session(admin()).header("Origin", "https://evil.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void loginRealUsaTokenDeFormularioYRotaIdentificadorDeSesion() throws Exception {
        var pagina = mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\""))).andReturn();
        var sesion = (MockHttpSession) pagina.getRequest().getSession(false);
        String idAnterior = sesion.getId();
        CsrfToken token = (CsrfToken) pagina.getRequest().getAttribute("_csrf");
        mvc.perform(post("/login").session(sesion).param("usuario", "admin").param("clave", "admin123")
                        .param(token.getParameterName(), token.getToken()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        assertThat(sesion.getId()).isNotEqualTo(idAnterior);
        mvc.perform(get("/api/v1/user").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/").session(sesion)).andExpect(content().string(containsString("name=\"_csrf_header\"")));
    }

    @Test
    void logoutExigePostConCsrfEInvalidaLaSesion() throws Exception {
        var sesion = admin();
        mvc.perform(post("/logout").session(sesion)).andExpect(status().isForbidden());
        assertThat(sesion.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(sesion).with(SesionesPrueba.csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void noSePuedeDeshabilitarAlUltimoAdministrador() throws Exception {
        mvc.perform(patch("/api/v1/user/1").session(admin()).with(SesionesPrueba.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Admin","correo":"admin@lambda.local","estado":"BLOQUEADO","rol":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0]").value(containsString("último administrador")));
    }
}
