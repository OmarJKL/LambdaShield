package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.model.EvaluacionResultado;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.entity.Cuenta;
import com.lambdashield.fraude.domain.enums.*;
import com.lambdashield.fraude.repository.ClienteRepository;
import com.lambdashield.fraude.repository.CuentaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class SaldoConcurrenteTest {
    @Autowired ClienteRepository clientes;
    @Autowired CuentaRepository cuentas;
    @Autowired EvaluacionTransaccionService operaciones;
    @Autowired PlatformTransactionManager gestor;
    @Autowired JdbcTemplate jdbc;
    private String codigo;
    private Long idCliente;
    private Long idCuenta;

    @BeforeEach
    void prepararCuentaAislada() {
        codigo = "T-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(gestor).executeWithoutResult(tx -> {
            Cliente cliente = clientes.save(Cliente.builder().codigoCliente(codigo).tipoDocumento("DNI")
                    .numeroDocumento(codigo).nombres("Concurrencia").promedioGasto(new BigDecimal("1000"))
                    .esActivo(true).build());
            idCliente = cliente.getIdCliente();
            idCuenta = cuentas.save(Cuenta.builder().cliente(cliente).numeroCuenta(codigo)
                    .tipoCuenta(TipoCuenta.AHORROS).codigoMoneda("PEN").estadoCuenta(EstadoCuenta.ACTIVA)
                    .saldoDisponible(new BigDecimal("100.00")).fechaApertura(LocalDate.now())
                    .fechaCreacion(OffsetDateTime.now()).build()).getIdCuenta();
        });
    }

    @AfterEach
    void retirarSoloLosDatosDeEstaPrueba() {
        new TransactionTemplate(gestor).executeWithoutResult(tx -> {
            jdbc.update("delete from evaluacion_regla where id_evaluacion in (select e.id_evaluacion from evaluacion_fraude e join transaccion t on t.id_transaccion=e.id_transaccion where t.id_cuenta=?)", idCuenta);
            jdbc.update("delete from evaluacion_fraude where id_transaccion in (select id_transaccion from transaccion where id_cuenta=?)", idCuenta);
            jdbc.update("delete from transaccion where id_cuenta=?", idCuenta);
            jdbc.update("delete from beneficiario where id_cliente=?", idCliente);
            jdbc.update("delete from dispositivo where id_cliente=?", idCliente);
            jdbc.update("delete from cuenta where id_cuenta=?", idCuenta);
            jdbc.update("delete from cliente where id_cliente=?", idCliente);
        });
    }

    private EvaluacionResultado crear(String monto) {
        return operaciones.crearYEvaluar(new EvaluarTransaccionCommand(codigo, new BigDecimal(monto),
                "servicios", 12, "local", "reconocido", 0, "recurrente", "conocida", null));
    }
    private BigDecimal saldo() { return cuentas.findById(idCuenta).orElseThrow().getSaldoDisponible(); }

    private <T> List<T> simultaneas(Supplier<T> accion) throws Exception {
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<T> trabajo = () -> {
            listos.countDown();
            if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin inicio");
            return accion.get();
        };
        try {
            Future<T> primera = ejecutor.submit(trabajo);
            Future<T> segunda = ejecutor.submit(trabajo);
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            return List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
        } finally { inicio.countDown(); ejecutor.shutdownNow(); }
    }

    @Test
    void dosDebitosConcurrentesNoSobrescribenSaldo() throws Exception {
        simultaneas(() -> crear("10.01"));
        assertThat(saldo()).isEqualByComparingTo("79.98");
    }

    @Test
    void saldoNoAlcanzaParaAmbosSoloUnoSeConfirma() throws Exception {
        var resultados = simultaneas(() -> {
            try { crear("70.00"); return true; }
            catch (SaldoInsuficienteException esperado) { return false; }
        });
        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        assertThat(saldo()).isEqualByComparingTo("30.00");
    }

    @Test
    void dobleDesactivacionYReactivacionNoDuplicanDinero() throws Exception {
        var evaluacion = crear("10.01");
        var reversos = simultaneas(() -> {
            try { operaciones.desactivarEvaluacion(evaluacion.idInterno()); return true; }
            catch (EvaluacionNoEncontradaException esperado) { return false; }
        });
        assertThat(reversos).containsExactlyInAnyOrder(true, false);
        assertThat(saldo()).isEqualByComparingTo("100.00");
        var reactivaciones = simultaneas(() -> {
            try { operaciones.activarEvaluacion(evaluacion.idInterno()); return true; }
            catch (IllegalArgumentException esperado) { return false; }
        });
        assertThat(reactivaciones).containsExactlyInAnyOrder(true, false);
        assertThat(saldo()).isEqualByComparingTo("89.99");
    }
}
