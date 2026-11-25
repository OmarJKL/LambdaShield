package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.AuthService;

import com.lambdashield.fraude.support.SesionesPrueba;

import com.lambdashield.fraude.domain.entity.Beneficiario;
import com.lambdashield.fraude.domain.entity.Cuenta;
import com.lambdashield.fraude.repository.BeneficiarioRepository;
import com.lambdashield.fraude.repository.ClienteRepository;
import com.lambdashield.fraude.repository.CuentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.domain.enums.EstadoCuenta;
import com.lambdashield.fraude.domain.enums.TipoCuenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comportamiento del portal del cliente: el tipo de destinatario se deriva del
 * historial real y el saldo se descuenta de la cuenta al aprobarse una
 * operación (y se rechaza si los fondos no alcanzan).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalClienteApiMockMvcTest {
    @Autowired
    private com.lambdashield.fraude.repository.TipoTransaccionRepository tipos;

    @Autowired
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private CuentaRepository cuentaRepository;
    @Autowired
    private BeneficiarioRepository beneficiarioRepository;
    @Autowired
    private com.lambdashield.fraude.application.service.EvaluacionTransaccionService operaciones;
    @Autowired
    private com.lambdashield.fraude.repository.EvaluacionFraudeRepository evaluaciones;

    private MockHttpSession sesion(String rol) {
        return SesionesPrueba.crear(authService, rol);
    }

    private static String json(String cliente, double monto, String beneficiarioTipo, String beneficiarioNombre) {
        return """
                {
                  "cliente": "%s",
                  "monto": %s,
                  "categoria": "retail",
                  "hora": 12,
                  "pais": "local",
                  "dispositivo": "reconocido",
                  "operacionesUltimaHora": 0,
                  "beneficiario": "%s",
                  "ip": "conocida",
                  "beneficiarioNombre": "%s"
                }
                """.formatted(cliente, monto, beneficiarioTipo, beneficiarioNombre);
    }

    @Test
    void tipoDeBeneficiarioSeDerivaDelHistorial() throws Exception {
        Long idCliente = clienteRepository.findByCodigoCliente("C-001").orElseThrow().getIdCliente();

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf())
                        .session(sesion("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", 20, "", "Carlos Mendoza")))
                .andExpect(status().isOk());

        Beneficiario primero = beneficiarioRepository
                .findFirstByCliente_IdClienteAndNombreBeneficiario(idCliente, "Carlos Mendoza").orElseThrow();
        assertThat(primero.isEsConocido())
                .as("la primera transferencia a un destinatario lo marca como nuevo")
                .isFalse();

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf())
                        .session(sesion("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", 20, "", "Carlos Mendoza")))
                .andExpect(status().isOk());

        Beneficiario segundo = beneficiarioRepository
                .findFirstByCliente_IdClienteAndNombreBeneficiario(idCliente, "Carlos Mendoza").orElseThrow();
        assertThat(segundo.isEsConocido())
                .as("al repetir transferencias al mismo destinatario pasa a frecuente")
                .isTrue();
    }

    @Test
    void operacionAprobadaDescuentaElSaldoDeLaCuenta() throws Exception {
        BigDecimal saldoAntes = saldoDe("C-001");

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf())
                        .session(sesion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", 1000, "recurrente", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("bajo"));

        BigDecimal saldoDespues = saldoDe("C-001");
        assertThat(saldoDespues).isEqualByComparingTo(saldoAntes.subtract(new BigDecimal("1000.00")));
    }

    @Test
    void operacionConSaldoInsuficienteSeRechaza() throws Exception {
        BigDecimal saldo = saldoDe("C-001");
        double montoExcesivo = saldo.doubleValue() + 1000;

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf())
                        .session(sesion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", montoExcesivo, "recurrente", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value(org.hamcrest.Matchers.containsString("Saldo insuficiente")));
    }

    @Test
    void clienteNoPuedeAumentarSuSaldoSeleccionandoUnaCategoriaDeAbono() throws Exception {
        tipos.save(com.lambdashield.fraude.domain.entity.TipoTransaccion.builder().codigoTipo("abono_prueba")
                .nombreTipo("Abono de prueba").naturaleza(com.lambdashield.fraude.domain.enums.NaturalezaTransaccion.CREDITO)
                .esActivo(true).build());
        BigDecimal saldoAntes = saldoDe("C-001");
        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", 1, "recurrente", "").replace("retail", "abono_prueba")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0]")
                        .value(org.hamcrest.Matchers.containsString("solo permite operaciones de débito")));
        assertThat(saldoDe("C-001")).isEqualByComparingTo(saldoAntes);
    }

    @Test
    void clienteNoPuedeCrearCategoriasNuevasDesdeElPortal() throws Exception {
        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("C-001", 1, "recurrente", "").replace("retail", "categoria_inventada")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("La categoría seleccionada no está disponible"));
        assertThat(tipos.findByCodigoTipo("categoria_inventada")).isEmpty();
    }

    @Test
    void cuentaDestinoSeReconoceConOSinGuionesSinDuplicarElContacto() throws Exception {
        for (String numero : new String[]{"001101450200847292", "0011 0145 0200847292"}) {
            mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion("CLIENTE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("C-001", 1, "", "").replace("\"beneficiarioNombre\": \"\"",
                                    "\"numeroCuentaDestino\": \"" + numero + "\", \"bancoDestino\": \"lambda\"")))
                    .andExpect(status().isOk());
        }
        Long idCliente = clienteRepository.findByCodigoCliente("C-001").orElseThrow().getIdCliente();
        assertThat(beneficiarioRepository.buscarPorCuentaNormalizada(idCliente, "001101450200847292"))
                .singleElement().satisfies(b -> {
                    assertThat(b.getNumeroCuentaDestino()).isEqualTo("0011-0145-0200847292");
                    assertThat(b.getNombreBeneficiario()).isEqualTo("Jorge Salas Mendoza");
                });
        mockMvc.perform(get("/api/v1/account/destination").param("numero", "001101450200847292").session(sesion("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numeroCuenta").value("0011-0145-0200847292"));
    }

    @Test
    void operacionUsaLaPrimeraCuentaActivaYSinCuentaActivaSeRechazaCon400() throws Exception {
        var cliente = clienteRepository.findByCodigoCliente("C-001").orElseThrow();
        Cuenta primera = cuentaRepository.findTopByCliente_IdClienteOrderByIdCuentaAsc(cliente.getIdCliente()).orElseThrow();
        primera.setEstadoCuenta(EstadoCuenta.BLOQUEADA);
        Cuenta segunda = cuentaRepository.save(Cuenta.builder().cliente(cliente).numeroCuenta("0011-0145-0299999999")
                .tipoCuenta(TipoCuenta.AHORROS).codigoMoneda("PEN").saldoDisponible(new BigDecimal("100.00"))
                .estadoCuenta(EstadoCuenta.ACTIVA).fechaApertura(LocalDate.now()).fechaCreacion(OffsetDateTime.now()).build());

        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("C-001", 1, "recurrente", "")))
                .andExpect(status().isOk());
        assertThat(segunda.getSaldoDisponible()).isEqualByComparingTo("99.00");

        segunda.setEstadoCuenta(EstadoCuenta.BLOQUEADA);
        mockMvc.perform(post("/api/v1/evaluation").with(SesionesPrueba.csrf()).session(sesion("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("C-001", 1, "recurrente", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("El cliente C-001 no tiene una cuenta activa"));
    }

    @Test
    void horaDeclaradaPosteriorALaActualSeRegistraElDiaAnterior() {
        OffsetDateTime antes = OffsetDateTime.now();
        var resultado = operaciones.crearYEvaluar(new EvaluarTransaccionCommand("C-001", new BigDecimal("1.00"),
                "servicios", 23, "local", "reconocido", 0, "recurrente", "conocida", null));
        OffsetDateTime registrada = evaluaciones.findById(resultado.idInterno()).orElseThrow()
                .getTransaccion().getFechaHoraTransaccion();
        // A las 23:xx corresponde al mismo día; antes de esa hora, al día anterior.
        assertThat(registrada).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(registrada.getHour()).isEqualTo(23);
        assertThat(registrada.toLocalDate()).isEqualTo(antes.getHour() == 23 ? antes.toLocalDate() : antes.toLocalDate().minusDays(1));
    }

    private BigDecimal saldoDe(String codigoCliente) {
        Cuenta cuenta = cuentaRepository
                .findFirstByCliente_IdClienteOrderByIdCuentaAsc(
                        clienteRepository.findByCodigoCliente(codigoCliente).orElseThrow().getIdCliente())
                .orElseThrow();
        return cuenta.getSaldoDisponible();
    }
}
