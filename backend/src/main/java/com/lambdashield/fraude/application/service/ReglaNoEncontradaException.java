package com.lambdashield.fraude.application.service;

public class ReglaNoEncontradaException extends RuntimeException {
    public ReglaNoEncontradaException() { super("La regla no existe."); }
}
