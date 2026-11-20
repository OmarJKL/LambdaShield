package com.lambdashield.fraude.domain.enums;



public enum NivelRiesgo {
    BAJO(DecisionFraude.APROBAR, EstadoTransaccion.APROBADA),
    MEDIO(DecisionFraude.MONITOREAR, EstadoTransaccion.APROBADA),
    ALTO(DecisionFraude.RETENER, EstadoTransaccion.RETENIDA),
    CRITICO(DecisionFraude.BLOQUEAR, EstadoTransaccion.BLOQUEADA);

    private final DecisionFraude decision;
    private final EstadoTransaccion estado;
    NivelRiesgo(DecisionFraude decision, EstadoTransaccion estado) {
        this.decision = decision;
        this.estado = estado;
    }
    public DecisionFraude decision() { return decision; }
    public EstadoTransaccion estadoTransaccion() { return estado; }
}
