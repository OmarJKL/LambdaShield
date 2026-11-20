package com.lambdashield.fraude.application.service;

/** Mensajes controlados, nunca incluyen claves ni cuerpos del proveedor. */
public class AsistenteReglasException extends RuntimeException {
    private final boolean noDisponible;
    public AsistenteReglasException(String mensaje, boolean noDisponible) {
        super(mensaje);
        this.noDisponible = noDisponible;
    }
    public boolean noDisponible() { return noDisponible; }
}
