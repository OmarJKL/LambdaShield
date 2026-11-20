package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.service.*;
import com.lambdashield.fraude.domain.entity.*;
import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import com.lambdashield.fraude.repository.*;
import com.lambdashield.fraude.presentation.security.SessionValidationFilter;
import com.lambdashield.fraude.support.SesionesPrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacionesApiMockMvcTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired EvaluacionTransaccionService operaciones;
    @Autowired NotificacionService notificaciones;
    @Autowired NotificacionUsuarioRepository avisos;
    @Autowired UsuarioSistemaRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired CuentaRepository cuentas;
    @Autowired BeneficiarioRepository beneficiarios;

    private MockHttpSession sesion(String nombre) {
        var usuario = auth.obtenerSesionActiva(nombre).orElseThrow();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(SessionValidationFilter.authentication(usuario));
        var sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        SessionValidationFilter.actualizarSesion(sesion, usuario);
        return sesion;
    }
    private Long bloqueada(String cliente) {
        return operaciones.crearYEvaluar(new EvaluarTransaccionCommand(cliente, new BigDecimal("20000.00"),
                "cripto", 2, "alto_riesgo", "nuevo", 6, "nuevo", "nueva", null)).idInterno();
    }
    private NotificacionUsuario aviso(String usuario, Long evaluacion) {
        return avisos.findAll().stream().filter(n -> n.getUsuario().getNombreUsuario().equals(usuario)
                && n.getEvaluacion().getIdEvaluacion().equals(evaluacion)).findFirst().orElseThrow();
    }
    @Test
    void bloqueoNotificaATodosLosAccesosDelTitularSinDescontarSaldo() throws Exception {
        var cliente = clientes.findByCodigoCliente("C-001").orElseThrow();
        BigDecimal antes = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(cliente.getIdCliente()).orElseThrow().getSaldoDisponible();
        long anteriores = notificaciones.listar("usuario", 0).noLeidas();
        Long id = bloqueada("C-001");
        assertThat(aviso("usuario", id).getTipo()).isEqualTo(EstadoTransaccion.BLOQUEADA);
        assertThat(aviso("maria", id).getTipo()).isEqualTo(EstadoTransaccion.BLOQUEADA);
        assertThat(cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(cliente.getIdCliente()).orElseThrow().getSaldoDisponible()).isEqualByComparingTo(antes);
        mvc.perform(get("/api/v1/notification").session(sesion("usuario")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.noLeidas").value(anteriores + 1))
                .andExpect(jsonPath("$.items[0].operacion").value(String.format("TR-%05d", id)))
                .andExpect(jsonPath("$.items[0].mensaje").value(containsString("saldo no fue descontado")))
                .andExpect(jsonPath("$.items[0].mensaje").value(containsString("Factores:")));
    }
    @Test
    void retencionTambienAvisaYAprobadaNoCreaAviso() {
        long antes = notificaciones.listar("usuario", 0).noLeidas();
        var retenida = operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("540"),
                "servicios", 2, "local", "nuevo", 3, "recurrente", "nueva", null));
        assertThat(retenida.nivel()).isEqualTo("alto");
        assertThat(aviso("usuario", retenida.idInterno()).getTipo()).isEqualTo(EstadoTransaccion.RETENIDA);
        operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("1"),
                "servicios", 12, "local", "reconocido", 0, "recurrente", "conocida", null));
        assertThat(notificaciones.listar("usuario", 0).noLeidas()).isEqualTo(antes + 1);
    }
    @Test
    void lecturaEsIndividualYNoPermiteLeerAvisosAjenos() throws Exception {
        Long propia = bloqueada("C-001"), ajena = bloqueada("C-002");
        Long id = aviso("usuario", propia).getIdNotificacion();
        Long otro = aviso("cliente", ajena).getIdNotificacion();
        long maria = notificaciones.listar("maria", 0).noLeidas();
        mvc.perform(patch("/api/v1/notification/{id}/leer", otro).session(sesion("usuario")).with(SesionesPrueba.csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notification/{id}/leer", id).session(sesion("usuario")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notification/{id}/leer", id).session(sesion("usuario")).with(SesionesPrueba.csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/notification/{id}/leer", id).session(sesion("usuario")).with(SesionesPrueba.csrf()))
                .andExpect(status().isNoContent());
        assertThat(notificaciones.listar("usuario", 0).items().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow().leida()).isTrue();
        assertThat(notificaciones.listar("maria", 0).noLeidas()).isEqualTo(maria);
        assertThat(aviso("cliente", ajena).getFechaLectura()).isNull();
    }
    @Test
    void leerTodasSoloAfectaAlUsuarioAutenticado() throws Exception {
        bloqueada("C-001");
        long maria = notificaciones.listar("maria", 0).noLeidas();
        mvc.perform(patch("/api/v1/notification/leer-todas").session(sesion("usuario")).with(SesionesPrueba.csrf()))
                .andExpect(status().isNoContent());
        assertThat(notificaciones.listar("usuario", 0).noLeidas()).isZero();
        assertThat(notificaciones.listar("maria", 0).noLeidas()).isEqualTo(maria);
    }
    @Test
    void bandejaNoSeExponeAAdministradoresAnalistasOSesionesAusentes() throws Exception {
        mvc.perform(get("/api/v1/notification")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notification").session(sesion("admin"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notification").session(sesion("analista"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notification?pagina=-1").session(sesion("usuario"))).andExpect(status().isBadRequest());
    }
    @Test
    void portalMuestraCampanaYContactosPropiosConCuentaCompleta() throws Exception {
        var titular = clientes.findByCodigoCliente("C-001").orElseThrow();
        beneficiarios.save(Beneficiario.builder().cliente(titular).nombreBeneficiario("Jorge Salas Mendoza")
                .numeroCuentaDestino("0011-0145-0200847292").entidadFinanciera("Lambda Shield")
                .esActivo(true).esConocido(true).build());
        mvc.perform(get("/operacion").session(sesion("usuario"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btnNotificaciones\"")))
                .andExpect(content().string(containsString("id=\"contactoGuardado\"")))
                .andExpect(content().string(containsString("0011-0145-0200847292")));
        mvc.perform(get("/operacion").session(sesion("cliente"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-cuenta=\"0011-0145-0200847292\""))));
    }
    @Test
    void numeroDestinoSePersisteYSeResuelveNombreInternoEnServidor() {
        var resultado = operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("1"),
                "servicios", 12, "local", "reconocido", 0, "recurrente", "conocida", "Nombre falso",
                "0011-0145-0200847292", "bcp"));
        var cliente = clientes.findByCodigoCliente("C-001").orElseThrow();
        var contacto = beneficiarios.findFirstByCliente_IdClienteAndNumeroCuentaDestino(cliente.getIdCliente(), "0011-0145-0200847292").orElseThrow();
        assertThat(contacto.getNombreBeneficiario()).isEqualTo("Jorge Salas Mendoza");
        assertThat(contacto.getEntidadFinanciera()).isEqualTo("Lambda Shield");
        assertThat(resultado.nivel()).isEqualTo("bajo");
    }
    @Test
    void cuentaPropiaNoSeAceptaComoDestino() {
        assertThatThrownBy(() -> operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("1"),
                "servicios", 12, "local", "reconocido", 0, "recurrente", "conocida", "Yo mismo",
                "0011-0145-0200847291", "lambda"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otro destinatario");
    }
    @Test void destinatarioDesactivadoNoSePuedeReutilizarPorNombre() {
        var titular = clientes.findByCodigoCliente("C-001").orElseThrow();
        beneficiarios.save(Beneficiario.builder().cliente(titular).nombreBeneficiario("Destinatario desactivado")
                .entidadFinanciera("Lambda Shield").esActivo(false).esConocido(true).build());
        assertThatThrownBy(() -> operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("1"),
                "servicios", 12, "local", "reconocido", 0, "recurrente", "conocida", "Destinatario desactivado")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desactivado");
    }
    @Test
    void consultaDeTitularExigeSesionYDevuelveSoloDatosDeDestino() throws Exception {
        mvc.perform(get("/api/v1/account/destination").param("numero", "0011-0145-0200847292"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/destination").param("numero", "0011-0145-0200847292").session(sesion("usuario")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Jorge Salas Mendoza"))
                .andExpect(jsonPath("$.banco").value("lambda")).andExpect(jsonPath("$.saldoDisponible").doesNotExist());
        mvc.perform(get("/api/v1/account/destination").param("numero", "0011-0145-0200847291").session(sesion("usuario")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/destination").param("numero", "9999999999").session(sesion("usuario")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/destination").param("numero", "incorrecto").session(sesion("usuario")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/account/destination").param("numero", "0011-0145-0200847292").session(sesion("analista")))
                .andExpect(status().isForbidden());
        var destino = cuentas.findByNumeroCuenta("0011-0145-0200847292").orElseThrow();
        destino.setEstadoCuenta(com.lambdashield.fraude.domain.enums.EstadoCuenta.BLOQUEADA);
        cuentas.saveAndFlush(destino);
        mvc.perform(get("/api/v1/account/destination").param("numero", "0011-0145-0200847292").session(sesion("usuario")))
                .andExpect(status().isNotFound());
    }
}
