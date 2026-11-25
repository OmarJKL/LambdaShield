package com.lambdashield.fraude.application.validation;

import java.nio.charset.StandardCharsets;

/** Contrato compartido por las entradas web y las llamadas directas a los casos de uso. */
public final class PoliticaClave {
    public static final int MAX_BYTES = 72;
    public static final String MENSAJE = "La clave debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8";

    private PoliticaClave() {}

    public static boolean longitudAdmitida(String clave) {
        return clave != null && clave.length() <= MAX_BYTES
                && clave.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public static boolean esValida(String clave) {
        return longitudAdmitida(clave) && !clave.isBlank() && clave.codePointCount(0, clave.length()) >= 8;
    }

    public static void validar(String clave) {
        if (!esValida(clave)) throw new IllegalArgumentException(MENSAJE);
    }
}
