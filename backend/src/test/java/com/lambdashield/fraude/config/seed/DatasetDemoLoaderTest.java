package com.lambdashield.fraude.config.seed;

import com.lambdashield.fraude.application.service.AuthService;
import com.lambdashield.fraude.application.service.EvaluacionConsultaService;
import com.lambdashield.fraude.application.service.EvaluacionTransaccionService;
import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import com.lambdashield.fraude.domain.enums.EstadoUsuario;
import com.lambdashield.fraude.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatasetDemoLoaderTest {
    @Autowired DatasetDemoLoader loader;
    @Autowired ClienteRepository clientes;
    @Autowired CuentaRepository cuentas;
    @Autowired UsuarioSistemaRepository usuarios;
    @Autowired TransaccionRepository transacciones;
    @Autowired EvaluacionFraudeRepository evaluaciones;
    @Autowired EvaluacionConsultaService consulta;
    @Autowired EvaluacionTransaccionService operaciones;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired BeneficiarioRepository beneficiarios;
    @Autowired com.lambdashield.fraude.application.service.DashboardMovimientosService movimientos;

    @Test
    void creaOchoCuentasConSaldosHistorialYTodasLasCredenciales() {
        long clientesAntes = clientes.count(), cuentasAntes = cuentas.count(), usuariosAntes = usuarios.count();
        long txAntes = transacciones.count();
        var resumen = loader.cargar();
        em.flush();
        assertThat(resumen.transacciones()).isEqualTo(5252);
        assertThat(clientes.count()).isEqualTo(clientesAntes + 8);
        assertThat(cuentas.count()).isEqualTo(cuentasAntes + 8);
        assertThat(usuarios.count()).isEqualTo(usuariosAntes + 10);
        assertThat(transacciones.count()).isEqualTo(txAntes + 5252);
        for (var persona : DatasetDemo.PERSONAS) {
            var cliente = clientes.findByCodigoCliente(persona.codigo()).orElseThrow();
            var cuenta = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(cliente.getIdCliente()).orElseThrow();
            BigDecimal esperado = transacciones.findAll().stream()
                    .filter(t -> t.getCuenta().getIdCuenta().equals(cuenta.getIdCuenta())
                            && t.getEstadoTransaccion() == EstadoTransaccion.APROBADA)
                    .map(t -> t.getTipoTransaccion().getNaturaleza() == com.lambdashield.fraude.domain.enums.NaturalezaTransaccion.CREDITO
                            ? t.getMontoTransaccion() : t.getMontoTransaccion().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(cuenta.getNumeroCuenta()).isEqualTo(persona.cuenta());
            var agenda = beneficiarios.findByCliente_CodigoClienteAndEsActivoTrueAndNumeroCuentaDestinoIsNotNullOrderByNombreBeneficiarioAsc(persona.codigo());
            assertThat(agenda).hasSize(7).noneMatch(b -> b.getNumeroCuentaDestino().equals(persona.cuenta()));
            assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo(esperado);
            var historial = consulta.listarPorClienteCodigo(persona.codigo());
            assertThat(historial).hasSizeBetween(654, 658);
            Map<String, Long> niveles = historial.stream().map(t -> t.nivel())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            assertThat(niveles).containsEntry("bajo", (long) historial.size() - 3).containsEntry("medio", 1L)
                    .containsEntry("alto", 1L).containsEntry("critico", 1L);
            assertThat(historial).allMatch(t -> t.factores().size() == 8);
            assertThat(auth.autenticar(persona.usuario(), persona.clave())).get()
                    .extracting(AuthService.UsuarioSesion::codigoCliente).isEqualTo(persona.codigo());
        }
        assertThat(movimientos.generarMovimientos(365)).hasSize(366)
                .allMatch(d -> d.ingreso().signum() > 0 && d.egreso().signum() > 0);
        assertThat(auth.autenticar(DatasetDemo.ADMIN, DatasetDemo.CLAVE_ADMIN)).get()
                .extracting(AuthService.UsuarioSesion::rol).isEqualTo("ADMIN");
        assertThat(auth.autenticar(DatasetDemo.ANALISTA, DatasetDemo.CLAVE_ANALISTA)).get()
                .extracting(AuthService.UsuarioSesion::rol).isEqualTo("ANALISTA");
    }

    @Test
    void repetirNoDuplicaNiRestableceClavesEstadosOOperacionesDesactivadas() {
        loader.cargar();
        long total = transacciones.count();
        var persona = DatasetDemo.PERSONAS.getFirst();
        var usuario = usuarios.findByNombreUsuario(persona.usuario()).orElseThrow();
        usuario.setHashPassword(encoder.encode("ClaveModificada!2026"));
        usuario.setEstadoUsuario(EstadoUsuario.BLOQUEADO);
        var evaluacion = consulta.listarPorClienteCodigo(persona.codigo()).getFirst();
        operaciones.desactivarEvaluacion(evaluacion.idInterno());
        var cuenta = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(usuario.getIdCliente()).orElseThrow();
        BigDecimal saldoTrasReverso = cuenta.getSaldoDisponible();
        assertThat(loader.cargar().transacciones()).isZero();
        em.flush();
        em.clear();
        var conservado = usuarios.findByNombreUsuario(persona.usuario()).orElseThrow();
        assertThat(conservado.getEstadoUsuario()).isEqualTo(EstadoUsuario.BLOQUEADO);
        assertThat(encoder.matches("ClaveModificada!2026", conservado.getHashPassword())).isTrue();
        assertThat(transacciones.count()).isEqualTo(total);
        assertThat(cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(conservado.getIdCliente()).orElseThrow().getSaldoDisponible())
                .isEqualByComparingTo(saldoTrasReverso);
        assertThat(evaluaciones.findById(evaluacion.idInterno()).orElseThrow().getTransaccion().getEstadoTransaccion())
                .isEqualTo(EstadoTransaccion.REVERSADA);
    }
}
