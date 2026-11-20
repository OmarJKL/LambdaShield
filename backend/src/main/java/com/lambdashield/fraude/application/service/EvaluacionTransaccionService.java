package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.mapper.EvaluacionResultadoMapper;
import com.lambdashield.fraude.application.model.EvaluacionResultado;
import com.lambdashield.fraude.application.model.FactorResultado;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.entity.Cuenta;
import com.lambdashield.fraude.domain.entity.EvaluacionFraude;
import com.lambdashield.fraude.domain.entity.EvaluacionRegla;
import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.domain.entity.Transaccion;
import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import com.lambdashield.fraude.domain.enums.NivelRiesgo;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import com.lambdashield.fraude.repository.CuentaRepository;
import com.lambdashield.fraude.repository.EvaluacionFraudeRepository;
import com.lambdashield.fraude.repository.EvaluacionReglaRepository;
import com.lambdashield.fraude.repository.TransaccionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio principal de evaluaciones.
 *
 * La evaluación, sus factores, la transacción y el cliente se persisten en las
 * tablas PostgreSQL del proyecto. No se conserva historial en memoria.
 */
@Service
public class EvaluacionTransaccionService {

    private final DeteccionFraudeService deteccionFraudeService;
    private final ClienteService clienteService;
    private final ClienteContextService clienteContextService;
    private final TransaccionReferenciasResolver referenciasResolver;
    private final TransaccionRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoSaldoService saldos;
    private final NotificacionService notificaciones;
    private final CatalogoReglasService catalogoReglas;
    private final EvaluacionFraudeRepository evaluacionFraudeRepository;
    private final EvaluacionReglaRepository evaluacionReglaRepository;
    private final EvaluacionResultadoMapper resultadoMapper;

    public EvaluacionTransaccionService(DeteccionFraudeService deteccionFraudeService,
                              ClienteService clienteService,
                              ClienteContextService clienteContextService,
                              TransaccionReferenciasResolver referenciasResolver,
                              TransaccionRepository transaccionRepository,
                              CuentaRepository cuentaRepository,
                              MovimientoSaldoService saldos,
                              NotificacionService notificaciones,
                              CatalogoReglasService catalogoReglas,
                              EvaluacionFraudeRepository evaluacionFraudeRepository,
                              EvaluacionReglaRepository evaluacionReglaRepository,
                              EvaluacionResultadoMapper resultadoMapper) {
        this.deteccionFraudeService = deteccionFraudeService;
        this.clienteService = clienteService;
        this.clienteContextService = clienteContextService;
        this.referenciasResolver = referenciasResolver;
        this.transaccionRepository = transaccionRepository;
        this.cuentaRepository = cuentaRepository;
        this.saldos = saldos;
        this.notificaciones = notificaciones;
        this.catalogoReglas = catalogoReglas;
        this.evaluacionFraudeRepository = evaluacionFraudeRepository;
        this.evaluacionReglaRepository = evaluacionReglaRepository;
        this.resultadoMapper = resultadoMapper;
    }

    @Transactional
    public EvaluacionResultado crearYEvaluar(EvaluarTransaccionCommand request) {
        Cliente cliente = obtenerCliente(request.cliente());
        return crearYEvaluar(request, OffsetDateTime.now(), cliente, "127.0.0.1", null, false);
    }

    /**
     * Evaluación para el portal del cliente: hora, frecuencia, dispositivo e IP
     * se derivan en el servidor y no se confía en los valores del body.
     */
    @Transactional
    public EvaluacionResultado crearYEvaluarCliente(EvaluarTransaccionCommand request, String codigoCliente,
                                                   String userAgent, String ipRemota) {
        Cliente cliente = obtenerCliente(codigoCliente);
        OffsetDateTime fechaHora = OffsetDateTime.now();

        long operacionesUltimaHora = evaluacionFraudeRepository
                .countByEsActivaTrueAndTransaccion_Cuenta_Cliente_CodigoClienteAndFechaEvaluacionAfter(
                        codigoCliente, fechaHora.minusHours(1));

        String dispositivo = clienteContextService.esDispositivoConocido(codigoCliente, userAgent)
                ? "reconocido"
                : "nuevo";
        String ip = clienteContextService.esIpConocida(codigoCliente, ipRemota)
                ? "conocida"
                : "nueva";

        EvaluarTransaccionCommand derivada = new EvaluarTransaccionCommand(
                codigoCliente,
                request.monto(),
                request.categoria(),
                fechaHora.getHour(),
                request.pais(),
                dispositivo,
                (int) Math.min(Integer.MAX_VALUE, operacionesUltimaHora),
                request.beneficiario(),
                ip,
                request.beneficiarioNombre(), request.numeroCuentaDestino(), request.bancoDestino()
        );

        return crearYEvaluar(derivada, fechaHora, cliente, ipRemota, userAgent, true);
    }

    private EvaluacionResultado crearYEvaluar(EvaluarTransaccionCommand request, OffsetDateTime fechaHora,
                                             Cliente cliente, String direccionIp, String userAgent, boolean soloDebito) {
        double promedio = cliente.getPromedioGasto() != null
                ? cliente.getPromedioGasto().doubleValue()
                : 0.0;

        Cuenta cuenta = clienteService.obtenerCuentaPrincipal(cliente);

        // Se resuelven las referencias antes de evaluar: el tipo de beneficiario
        // derivado del historial real debe alimentar las reglas del motor.
        TransaccionReferenciasResolver.Referencias referencias =
                referenciasResolver.resolver(cliente, request, userAgent);
        if (soloDebito && referencias.tipoTransaccion().getNaturaleza()
                != com.lambdashield.fraude.domain.enums.NaturalezaTransaccion.DEBITO) {
            throw new IllegalArgumentException("El portal del cliente solo permite operaciones de débito");
        }
        String beneficiarioTipo = referencias.beneficiario().isEsConocido() ? "recurrente" : "nuevo";

        TransaccionEvaluable transaccionEvaluable = new TransaccionEvaluable(
                request.monto().doubleValue(), request.hora(), request.pais(), request.dispositivo(),
                request.operacionesUltimaHora(), beneficiarioTipo, request.ip());
        List<CatalogoReglasService.FactorConfigurado> configurados = catalogoReglas.aplicar(
                deteccionFraudeService.evaluar(transaccionEvaluable, promedio));
        List<FactorEvaluado> factoresEvaluados = configurados.stream()
                .map(CatalogoReglasService.FactorConfigurado::factor).toList();
        int puntaje = deteccionFraudeService.calcularPuntajeTotal(factoresEvaluados);
        NivelRiesgo nivel = NivelRiesgo.valueOf(deteccionFraudeService.clasificarNivel(puntaje).toUpperCase(Locale.ROOT));
        EstadoTransaccion estado = nivel.estadoTransaccion();

        // El dinero solo se mueve en operaciones aprobadas; una retención o un
        // bloqueo no descuenta saldo. Si el saldo no alcanza, se rechaza.
        BigDecimal monto = request.monto().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal saldoAnterior = cuenta.getSaldoDisponible();
        BigDecimal saldoPosterior = saldoAnterior;
        if (estado == EstadoTransaccion.APROBADA) {
            saldos.aplicar(cuenta, monto, referencias.tipoTransaccion().getNaturaleza(), false);
            saldoPosterior = cuenta.getSaldoDisponible();
        }

        OffsetDateTime fechaTransaccion = fechaHora
                .withHour(request.hora())
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        Transaccion transaccion = Transaccion.builder()
                .codigoTransaccion(UUID.randomUUID())
                .cuenta(cuenta)
                .beneficiario(referencias.beneficiario())
                .tipoTransaccion(referencias.tipoTransaccion())
                .canal(referencias.canal())
                .dispositivo(referencias.dispositivo())
                .montoTransaccion(monto)
                .codigoMoneda("PEN")
                .fechaHoraTransaccion(fechaTransaccion)
                .saldoAnterior(saldoAnterior)
                .saldoPosterior(saldoPosterior)
                .direccionIp(direccionIp)
                .codigoPais(codigoPais(request.pais()))
                .ciudad("Lima")
                .estadoTransaccion(estado)
                .fechaRegistro(OffsetDateTime.now())
                .build();
        transaccionRepository.save(transaccion);

        EvaluacionFraude evaluacion = EvaluacionFraude.builder()
                .transaccion(transaccion)
                .fraudeScore(BigDecimal.valueOf(puntaje).setScale(2, RoundingMode.HALF_UP))
                .nivelRiesgo(nivel)
                .decision(nivel.decision())
                .esPosibleFraude(nivel == NivelRiesgo.ALTO || nivel == NivelRiesgo.CRITICO)
                .modeloUtilizado("Reglas RF001-RF008")
                .versionModelo("1.0")
                .fechaEvaluacion(fechaHora)
                .duracionMs(0)
                .esActiva(true)
                .build();
        evaluacionFraudeRepository.save(evaluacion);

        for (CatalogoReglasService.FactorConfigurado configurado : configurados) {
            FactorEvaluado factor = configurado.factor();
            ReglaFraude regla = configurado.regla();
            EvaluacionRegla evaluacionRegla = EvaluacionRegla.builder()
                    .evaluacion(evaluacion)
                    .regla(regla)
                    .maximoAplicado(factor.max())
                    .etiquetaAplicada(factor.etiqueta())
                    .versionAplicada(regla.getVersionRegla())
                    .seDisparo(factor.puntaje() > 0)
                    .valorDetectado(truncar(factor.motivo(), 250))
                    .pesoAplicado(BigDecimal.valueOf(factor.puntaje()).setScale(2, RoundingMode.HALF_UP))
                    .build();
            evaluacionReglaRepository.save(evaluacionRegla);
        }

        List<FactorResultado> factores = factoresEvaluados.stream()
                .map(resultadoMapper::toFactorResultado)
                .toList();
        notificaciones.notificar(evaluacion, factoresEvaluados);
        return resultadoMapper.toResultado(evaluacion, factores);
    }

    /**
     * Soft delete: la fila permanece en la base de datos, pero se marca como
     * inactiva y la transacción se registra como REVERSADA.
     */
    @Transactional
    public void desactivarEvaluacion(Long idEvaluacion) {
        EvaluacionFraude evaluacion = evaluacionFraudeRepository
                .bloquearPorId(idEvaluacion)
                .orElseThrow(() -> new EvaluacionNoEncontradaException(idEvaluacion));

        if (!evaluacion.isEsActiva()) throw new EvaluacionNoEncontradaException(idEvaluacion);
        Transaccion transaccion = evaluacion.getTransaccion();
        Cuenta cuenta = bloquearCuenta(transaccion);
        if (transaccion.getEstadoTransaccion() == EstadoTransaccion.APROBADA) {
            saldos.aplicar(cuenta, transaccion.getMontoTransaccion(), transaccion.getTipoTransaccion().getNaturaleza(), true);
        }

        evaluacion.setEsActiva(false);
        evaluacion.setFechaDesactivacion(OffsetDateTime.now());
        transaccion.setEstadoTransaccion(EstadoTransaccion.REVERSADA);
        evaluacionFraudeRepository.save(evaluacion);
        transaccionRepository.save(transaccion);
    }

    /**
     * Reactiva una evaluación dada de baja: vuelve a es_activa = true y la
     * transacción recupera el estado que le corresponde según su nivel de riesgo,
     * igual que al crearse (bajo/medio -> APROBADA, alto -> RETENIDA, crítico -> BLOQUEADA).
     */
    @Transactional
    public void activarEvaluacion(Long idEvaluacion) {
        EvaluacionFraude evaluacion = evaluacionFraudeRepository
                .bloquearPorId(idEvaluacion)
                .orElseThrow(() -> new EvaluacionNoEncontradaException(idEvaluacion));
        if (evaluacion.isEsActiva()) {
            throw new IllegalArgumentException("La evaluación ya está activa: " + idEvaluacion);
        }

        EstadoTransaccion estado = evaluacion.getNivelRiesgo().estadoTransaccion();
        Transaccion transaccion = evaluacion.getTransaccion();
        Cuenta cuenta = bloquearCuenta(transaccion);
        if (estado == EstadoTransaccion.APROBADA) {
            transaccion.setSaldoAnterior(cuenta.getSaldoDisponible());
            saldos.aplicar(cuenta, transaccion.getMontoTransaccion(), transaccion.getTipoTransaccion().getNaturaleza(), false);
            transaccion.setSaldoPosterior(cuenta.getSaldoDisponible());
        }

        evaluacion.setEsActiva(true);
        evaluacion.setFechaDesactivacion(null);
        transaccion.setEstadoTransaccion(estado);
        evaluacionFraudeRepository.save(evaluacion);
        transaccionRepository.save(transaccion);
    }

    private Cliente obtenerCliente(String codigoCliente) {
        return clienteService.buscarEntidad(codigoCliente)
                .orElseThrow(() -> new ClienteNoEncontradoException(codigoCliente));
    }

    private Cuenta bloquearCuenta(Transaccion transaccion) {
        return cuentaRepository.bloquearPorId(transaccion.getCuenta().getIdCuenta())
                .orElseThrow(() -> new IllegalStateException("Cuenta inexistente"));
    }

    private static String codigoPais(String pais) {
        return switch (pais) {
            case "local" -> "PE";
            case "regional" -> "CL";
            default -> "XX";
        };
    }

    private static String truncar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
