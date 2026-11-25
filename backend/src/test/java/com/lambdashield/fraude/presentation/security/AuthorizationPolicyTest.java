package com.lambdashield.fraude.presentation.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void clienteSoloPuedeCrearEvaluacionesPropias() {
        assertThat(policy.rechazoApi("CLIENTE", "POST", "/api/v1/evaluation")).isEmpty();
        assertThat(policy.rechazoApi("CLIENTE", "GET", "/api/v1/evaluation"))
                .contains("No autorizado");
    }

    @Test
    void analistaSoloLeeYNadieBorraEvaluaciones() {
        assertThat(policy.rechazoApi("ANALISTA", "GET", "/api/v1/metric")).isEmpty();
        assertThat(policy.rechazoApi("ANALISTA", "PATCH", "/api/v1/evaluation/1"))
                .contains("No autorizado");
        assertThat(policy.rechazoApi("ADMIN", "DELETE", "/api/v1/evaluation/1"))
                .contains("No autorizado");
    }

    @Test
    void gestionDeUsuariosEsExclusivaDeAdminIncluidaLaConsulta() {
        assertThat(policy.rechazoApi("ADMIN", "GET", "/api/v1/user")).isEmpty();
        assertThat(policy.rechazoApi("ADMIN", "DELETE", "/api/v1/user/1")).isEmpty();
        for (String rol : new String[]{"ANALISTA", "CLIENTE"}) {
            assertThat(policy.rechazoApi(rol, "GET", "/api/v1/user")).contains("No autorizado");
            assertThat(policy.rechazoApi(rol, "DELETE", "/api/v1/user/1")).contains("No autorizado");
        }
    }

    @Test
    void estadoDeReglasYAsistenteSonExclusivosDeAdmin() {
        assertThat(policy.rechazoApi("ADMIN", "PATCH", "/api/v1/rule/RF002/estado")).isEmpty();
        assertThat(policy.rechazoApi("ADMIN", "POST", "/api/v1/rule/assistant/review")).isEmpty();
        assertThat(policy.rechazoApi("ADMIN", "POST", "/api/v1/rule")).isEmpty();
        assertThat(policy.rechazoApi("ADMIN", "POST", "/api/v1/rule/simulation")).isEmpty();
        for (String rol : new String[]{"ANALISTA", "CLIENTE"}) {
            assertThat(policy.rechazoApi(rol, "POST", "/api/v1/rule")).contains("No autorizado");
            assertThat(policy.rechazoApi(rol, "POST", "/api/v1/rule/simulation")).contains("No autorizado");
            assertThat(policy.rechazoApi(rol, "PATCH", "/api/v1/rule/RF002/estado")).contains("No autorizado");
            assertThat(policy.rechazoApi(rol, "GET", "/api/v1/rule/assistant/status")).contains("No autorizado");
        }
    }

    @Test
    void cuentaDestinoYBandejaSonExclusivasDeCliente() {
        assertThat(policy.rechazoApi("CLIENTE", "GET", "/api/v1/account/destination")).isEmpty();
        assertThat(policy.rechazoApi("CLIENTE", "PATCH", "/api/v1/notification/5/leer")).isEmpty();
        for (String rol : new String[]{"ADMIN", "ANALISTA"}) {
            assertThat(policy.rechazoApi(rol, "GET", "/api/v1/account/destination")).contains("No autorizado");
            assertThat(policy.rechazoApi(rol, "GET", "/api/v1/notification")).contains("No autorizado");
        }
    }

    @Test
    void cadaRolSoloAccedeASusPaginas() {
        assertThat(policy.redireccionPagina("ADMIN", "/usuarios")).isEmpty();
        assertThat(policy.redireccionPagina("ANALISTA", "/usuarios")).contains("/");
        assertThat(policy.redireccionPagina("ANALISTA", "/simulador")).contains("/");
        assertThat(policy.redireccionPagina("ANALISTA", "/reportes")).isEmpty();
        assertThat(policy.redireccionPagina("CLIENTE", "/operacion")).isEmpty();
        assertThat(policy.redireccionPagina("CLIENTE", "/usuarios")).contains("/operacion");
        assertThat(policy.redireccionPagina(null, "/reportes")).contains("/login");
    }
}
