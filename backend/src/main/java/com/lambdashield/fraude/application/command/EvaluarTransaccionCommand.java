package com.lambdashield.fraude.application.command;

import java.math.BigDecimal;

/** Comando interno de aplicación para evaluar una operación; no depende del formato HTTP/JSON. */
public record EvaluarTransaccionCommand(
        String cliente,
        BigDecimal monto,
        String categoria,
        int hora,
        String pais,
        String dispositivo,
        int operacionesUltimaHora,
        String beneficiario,
        String ip,
        String beneficiarioNombre,
        String numeroCuentaDestino,
        String bancoDestino
) {
    public EvaluarTransaccionCommand(String cliente, BigDecimal monto, String categoria, int hora, String pais,
            String dispositivo, int operacionesUltimaHora, String beneficiario, String ip, String beneficiarioNombre) {
        this(cliente, monto, categoria, hora, pais, dispositivo, operacionesUltimaHora, beneficiario, ip,
                beneficiarioNombre, null, null);
    }
}
