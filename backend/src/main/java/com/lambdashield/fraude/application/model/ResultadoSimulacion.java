package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de probar un escenario sin registrar nada.
 *
 * @param vigente        evaluación con el catálogo activo actual
 * @param conCandidatas  evaluación añadiendo reglas candidatas o un borrador; null si no se indicaron
 * @param saldoDisponible saldo actual de la cuenta principal del cliente
 * @param fondosSuficientes si un débito aprobado por ese importe cabría en el saldo
 */
public record ResultadoSimulacion(String cliente, Evaluacion vigente, Evaluacion conCandidatas,
                                  BigDecimal saldoDisponible, boolean fondosSuficientes) {

    /** @param estado estado que tendría la transacción: APROBADA, RETENIDA o BLOQUEADA */
    public record Evaluacion(int puntaje, String nivel, String estado, List<FactorResultado> factores) {}
}
