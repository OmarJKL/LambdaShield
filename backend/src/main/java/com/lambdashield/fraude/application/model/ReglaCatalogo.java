package com.lambdashield.fraude.application.model;

/** Regla antifraude del catálogo persistido. */
public record ReglaCatalogo(String codigo, String nombre, String descripcion, int pesoMaximo, boolean activa) {}