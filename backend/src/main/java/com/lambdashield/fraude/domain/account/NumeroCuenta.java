package com.lambdashield.fraude.domain.account;

/** Forma comparable de un número de cuenta: los espacios y guiones solo son presentación. */
public final class NumeroCuenta {
    private NumeroCuenta() {}

    public static String normalizar(String numero) {
        return numero == null ? "" : numero.replaceAll("[\\s-]", "");
    }
}
