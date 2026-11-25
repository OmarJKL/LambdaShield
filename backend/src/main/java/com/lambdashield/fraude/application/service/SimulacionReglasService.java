package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.FactorResultado;
import com.lambdashield.fraude.application.model.ResultadoSimulacion;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.enums.NivelRiesgo;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba escenarios con el motor sin crear transacciones, evaluaciones, contactos ni
 * dispositivos y sin mover saldo. Compara el catálogo vigente con el que resultaría al
 * añadir reglas candidatas (inactivas) o un borrador del formulario.
 */
@Service
public class SimulacionReglasService {
    public static final int MAXIMO_ESCENARIOS = 100;

    private final ClienteService clientes;
    private final CatalogoReglasService catalogo;
    private final DeteccionFraudeService deteccion;

    public SimulacionReglasService(ClienteService clientes, CatalogoReglasService catalogo,
                                   DeteccionFraudeService deteccion) {
        this.clientes = clientes;
        this.catalogo = catalogo;
        this.deteccion = deteccion;
    }

    @Transactional(readOnly = true)
    public List<ResultadoSimulacion> simular(List<EvaluarTransaccionCommand> escenarios, Set<String> candidatas,
                                             DefinicionRegla borrador) {
        if (escenarios == null || escenarios.isEmpty() || escenarios.size() > MAXIMO_ESCENARIOS) {
            throw new IllegalArgumentException("Envía entre 1 y " + MAXIMO_ESCENARIOS + " escenarios");
        }
        Set<String> codigos = candidatas == null ? Set.of() : Set.copyOf(candidatas);
        if (borrador != null) borrador.validar();
        boolean comparar = !codigos.isEmpty() || borrador != null;
        Map<String, Cliente> cache = new HashMap<>();
        Map<String, BigDecimal> saldos = new HashMap<>();
        return escenarios.stream().map(e -> {
            Cliente cliente = cache.computeIfAbsent(e.cliente(), codigo -> clientes.buscarEntidad(codigo)
                    .orElseThrow(() -> new ClienteNoEncontradoException(codigo)));
            BigDecimal saldo = saldos.computeIfAbsent(e.cliente(), codigo -> clientes.buscar(codigo).orElseThrow().saldo());
            double promedio = cliente.getPromedioGasto() != null ? cliente.getPromedioGasto().doubleValue() : 0.0;
            TransaccionEvaluable transaccion = new TransaccionEvaluable(e.monto().doubleValue(), e.hora(), e.pais(),
                    e.dispositivo(), e.operacionesUltimaHora(),
                    "nuevo".equalsIgnoreCase(e.beneficiario()) ? "nuevo" : "recurrente", e.ip());
            var vigente = resultado(catalogo.evaluar(transaccion, promedio));
            var conCandidatas = comparar ? resultado(catalogo.evaluar(transaccion, promedio, codigos, borrador)) : null;
            return new ResultadoSimulacion(e.cliente(), vigente, conCandidatas, saldo, saldo.compareTo(e.monto()) >= 0);
        }).toList();
    }

    private ResultadoSimulacion.Evaluacion resultado(List<CatalogoReglasService.FactorConfigurado> configurados) {
        List<FactorEvaluado> factores = configurados.stream().map(CatalogoReglasService.FactorConfigurado::factor).toList();
        int puntaje = deteccion.calcularPuntajeTotal(factores);
        NivelRiesgo nivel = NivelRiesgo.valueOf(deteccion.clasificarNivel(puntaje).toUpperCase(Locale.ROOT));
        return new ResultadoSimulacion.Evaluacion(puntaje, nivel.name().toLowerCase(Locale.ROOT),
                nivel.estadoTransaccion().name(), factores.stream()
                .map(f -> new FactorResultado(f.id(), f.etiqueta(), f.puntaje(), f.max(), f.motivo())).toList());
    }
}
