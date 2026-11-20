package com.lambdashield.fraude.application.model;

import java.math.BigDecimal;
import java.util.List;

/** Definición del administrador; el asistente nunca la sustituye. */
public record DefinicionRegla(String nombre, String objetivo, Combinacion combinacion, int puntos,
                              List<Condicion> condiciones) {
    public DefinicionRegla {
        objetivo = objetivo == null ? "" : objetivo;
    }
    public enum Combinacion { TODAS, CUALQUIERA }
    public enum Campo { MONTO, RATIO_PROMEDIO, HORA, OPERACIONES_ULTIMA_HORA, PAIS, DISPOSITIVO, IP, BENEFICIARIO }
    public enum Operador { MAYOR, MAYOR_IGUAL, MENOR, MENOR_IGUAL, IGUAL }
    public record Condicion(Campo campo, Operador operador, String valor) {}
    public void validar() {
        if (nombre == null || nombre.isBlank() || nombre.length() > 150 || objetivo.length() > 1000
                || combinacion == null || puntos < 1 || puntos > 100 || condiciones == null
                || condiciones.isEmpty() || condiciones.size() > 8) throw invalida();
        for (Condicion c : condiciones) {
            if (c == null || c.campo() == null || c.operador() == null || c.valor() == null
                    || c.valor().isBlank() || c.valor().length() > 40) throw invalida();
            List<String> opciones = switch (c.campo()) {
                case PAIS -> List.of("local", "regional", "alto_riesgo");
                case DISPOSITIVO -> List.of("reconocido", "nuevo");
                case IP -> List.of("conocida", "nueva");
                case BENEFICIARIO -> List.of("recurrente", "nuevo");
                default -> List.of();
            };
            if (!opciones.isEmpty()) {
                if (c.operador() != Operador.IGUAL || !opciones.contains(c.valor())) throw invalida();
            } else {
                try {
                    BigDecimal numero = new BigDecimal(c.valor());
                    BigDecimal max = switch (c.campo()) {
                        case HORA -> BigDecimal.valueOf(23);
                        case OPERACIONES_ULTIMA_HORA -> BigDecimal.valueOf(1000);
                        case RATIO_PROMEDIO -> BigDecimal.valueOf(1000000);
                        default -> new BigDecimal("9999999999999999.99");
                    };
                    if (numero.signum() < 0 || numero.compareTo(max) > 0 || numero.scale() > 2
                            || (c.campo() == Campo.HORA || c.campo() == Campo.OPERACIONES_ULTIMA_HORA)
                            && numero.stripTrailingZeros().scale() > 0) throw invalida();
                } catch (NumberFormatException ex) { throw invalida(); }
            }
        }
    }
    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("Revisa nombre, puntos y condiciones: cada campo debe usar un operador y valor compatibles.");
    }
}
