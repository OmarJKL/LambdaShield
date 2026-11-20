package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.support.SesionesPrueba;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.domain.device.DeviceFingerprint;
import com.lambdashield.fraude.domain.entity.*;
import com.lambdashield.fraude.domain.enums.*;
import com.lambdashield.fraude.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsistenciaAplicacionTest {
    @Autowired EvaluacionTransaccionService operaciones;
    @Autowired EvaluacionConsultaService consultas;
    @Autowired DashboardResumenService resumen;
    @Autowired DashboardMovimientosService movimientos;
    @Autowired ClienteRepository clientes;
    @Autowired CuentaRepository cuentas;
    @Autowired ReglaFraudeRepository reglas;
    @Autowired EvaluacionFraudeRepository evaluaciones;
    @Autowired DispositivoRepository dispositivos;
    @Autowired TransaccionRepository transacciones;
    @Autowired DispositivoService dispositivoService;
    @Autowired UsuarioAdminService usuarios;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired AuthService authService;

    private EvaluarTransaccionCommand comando(String monto, String dispositivo) {
        return new EvaluarTransaccionCommand("C-001", new BigDecimal(monto), "servicios", 12,
                "local", dispositivo, 0, "recurrente", "conocida", null);
    }

    @Test
    void dashboardCuentaSoloAprobadasYConservaCentimos() {
        BigDecimal antes = resumen.obtenerResumen(7).egresos();
        BigDecimal serieAntes = movimientos.generarMovimientos(7).stream()
                .map(m -> m.egreso()).reduce(BigDecimal.ZERO, BigDecimal::add);
        operaciones.crearYEvaluar(comando("0.11", "reconocido"));
        for (EstadoTransaccion estado : new EstadoTransaccion[]{EstadoTransaccion.RETENIDA,
                EstadoTransaccion.BLOQUEADA, EstadoTransaccion.REVERSADA}) {
            var resultado = operaciones.crearYEvaluar(comando("0.22", "reconocido"));
            evaluaciones.findById(resultado.idInterno()).orElseThrow().getTransaccion().setEstadoTransaccion(estado);
        }
        em.flush();
        assertThat(resumen.obtenerResumen(7).egresos()).isEqualByComparingTo(antes.add(new BigDecimal("0.11")));
        assertThat(movimientos.generarMovimientos(7).stream().map(m -> m.egreso()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(serieAntes.add(new BigDecimal("0.11")));
        assertThat(transacciones.buscarDelPeriodo(OffsetDateTime.now().minusDays(7), OffsetDateTime.now().plusDays(1), Pageable.unpaged()))
                .allMatch(t -> t.getEstadoTransaccion() == EstadoTransaccion.APROBADA);
    }

    @Test
    void pesoYActivacionDelCatalogoGobiernanElMotorYElHistorialNoCambia() {
        ReglaFraude regla = reglas.findByCodigoRegla("RF002").orElseThrow();
        regla.setPeso(new BigDecimal("30"));
        regla.setNombreRegla("Dispositivo versión de prueba");
        var primera = operaciones.crearYEvaluar(comando("1.01", "nuevo"));
        assertThat(primera.puntaje()).isEqualTo(30);
        regla.setPeso(new BigDecimal("60"));
        regla.setNombreRegla("Nombre posterior");
        regla.setEsActiva(false);
        var segunda = operaciones.crearYEvaluar(comando("1.01", "nuevo"));
        assertThat(segunda.puntaje()).isZero();
        assertThat(segunda.factores()).noneMatch(f -> f.id().equals("dispositivo"));
        em.flush();
        em.clear();
        var historial = consultas.listar(true).stream().filter(e -> e.idInterno().equals(primera.idInterno())).findFirst().orElseThrow();
        assertThat(historial.factores()).isEqualTo(primera.factores());
        assertThat(historial.factores().stream().filter(f -> f.id().equals("dispositivo")).findFirst().orElseThrow().max()).isEqualTo(30);
    }

    @Test
    void catalogoIncompletoNoPierdeFactoresSilenciosamente() {
        ReglaFraude regla = reglas.findByCodigoRegla("RF008").orElseThrow();
        regla.setCodigoRegla("RETIRADA");
        regla.setEsActiva(false);
        assertThatThrownBy(() -> operaciones.crearYEvaluar(comando("1.00", "reconocido")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Falta la regla");
    }

    @Test
    void dispositivoSeReutilizaYNoSeConfundeConOtroConocido() {
        Cliente cliente = clientes.findByCodigoCliente("C-001").orElseThrow();
        String ua = "Mozilla Firefox/100 Linux";
        long antes = dispositivos.count();
        Dispositivo primero = dispositivoService.resolver(cliente, "nuevo", ua);
        Dispositivo segundo = dispositivoService.resolver(cliente, "nuevo", ua);
        assertThat(segundo.getIdDispositivo()).isEqualTo(primero.getIdDispositivo());
        assertThat(dispositivos.count()).isEqualTo(antes + 1);
        assertThat(dispositivoService.esConocido("C-001", ua)).isFalse();
        primero.setEsConocido(true);
        assertThat(dispositivoService.esConocido("C-001", ua)).isTrue();
        assertThat(dispositivoService.resolver(cliente, "reconocido", ua).getIdDispositivo()).isEqualTo(primero.getIdDispositivo());
        assertThat(dispositivoService.esConocido("C-001", "Safari/100 Mac OS")).isFalse();
    }

    @Test
    void huellaAntiguaConUuidSeReconoceYNormalizaSinCrearOtroRegistro() {
        Cliente cliente = clientes.findByCodigoCliente("C-001").orElseThrow();
        String ua = "Mozilla Safari/100 Mac OS";
        String huella = DeviceFingerprint.from("C-001", DeviceFingerprint.normalize(ua));
        Dispositivo antiguo = dispositivos.save(Dispositivo.builder().cliente(cliente)
                .huellaDispositivo(huella + "|uuid-anterior").esConocido(true).build());
        assertThat(dispositivoService.esConocido("C-001", ua)).isTrue();
        Dispositivo actual = dispositivoService.resolver(cliente, "reconocido", ua);
        assertThat(actual.getIdDispositivo()).isEqualTo(antiguo.getIdDispositivo());
        assertThat(actual.getHuellaDispositivo()).isEqualTo(huella);
    }

    @Test
    void reactivarYRevertirConservanSaldoDecimalYSonIdempotentesAnteRepeticion() {
        Cuenta cuenta = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(clientes.findByCodigoCliente("C-001").orElseThrow().getIdCliente()).orElseThrow();
        BigDecimal antes = cuenta.getSaldoDisponible();
        var operacion = operaciones.crearYEvaluar(comando("0.29", "reconocido"));
        assertThat(operacion.saldoDisponible()).isEqualByComparingTo(antes.subtract(new BigDecimal("0.29")));
        operaciones.desactivarEvaluacion(operacion.idInterno());
        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo(antes);
        operaciones.activarEvaluacion(operacion.idInterno());
        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo(antes.subtract(new BigDecimal("0.29")));
        assertThatThrownBy(() -> operaciones.activarEvaluacion(operacion.idInterno())).isInstanceOf(IllegalArgumentException.class);
        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo(antes.subtract(new BigDecimal("0.29")));
    }

    @Test
    void apiMantieneDecimalesQueNoCabenExactamenteEnDouble() throws Exception {
        Cuenta cuenta = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(clientes.findByCodigoCliente("C-001").orElseThrow().getIdCliente()).orElseThrow();
        cuenta.setSaldoDisponible(new BigDecimal("9999999999999999.99"));
        MockHttpSession sesion = SesionesPrueba.crear(authService, "ADMIN");
        String respuesta = mvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cliente":"C-001","monto":90071992547409.93,"categoria":"servicios","hora":12,
                        "pais":"local","dispositivo":"reconocido","operacionesUltimaHora":0,"beneficiario":"recurrente","ip":"conocida"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(respuesta).contains("\"monto\":90071992547409.93");
        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo("9909928007452590.06");
    }

    @Test
    void consultasDeHistorialYUsuariosNoCrecenPorCadaFila() {
        for (int i = 0; i < 4; i++) operaciones.crearYEvaluar(comando("0.01", "reconocido"));
        em.flush();
        em.clear();
        var estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean habilitadas = estadisticas.isStatisticsEnabled();
        try {
            estadisticas.setStatisticsEnabled(true);
            estadisticas.clear();
            assertThat(consultas.listarFiltrado(null, null, null, "C-001")).hasSizeGreaterThanOrEqualTo(4);
            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
            em.clear();
            estadisticas.clear();
            assertThat(usuarios.listar()).isNotEmpty();
            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
        } finally { estadisticas.setStatisticsEnabled(habilitadas); }
    }
}
