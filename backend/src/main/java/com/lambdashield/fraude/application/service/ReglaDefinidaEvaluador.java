package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.DefinicionRegla.Condicion;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ejecuta las reglas creadas desde el formulario (códigos RC). La definición es tipada:
 * campos, operadores y valores de una lista cerrada, nunca expresiones ni código.
 * Una regla definida es binaria: si se cumple aporta todo su máximo, si no aporta 0.
 */
@Component
public class ReglaDefinidaEvaluador {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public String serializar(DefinicionRegla definicion) {
        return JSON.writeValueAsString(definicion);
    }

    /** Definición guardada en parametros_json; vacía si no existe o no supera la validación actual. */
    public Optional<DefinicionRegla> leer(String parametrosJson) {
        if (parametrosJson == null || parametrosJson.isBlank()) return Optional.empty();
        try {
            DefinicionRegla definicion = JSON.readValue(parametrosJson, DefinicionRegla.class);
            definicion.validar();
            return Optional.of(definicion);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Texto legible de las condiciones, usado como descripción del catálogo. */
    public String resumen(DefinicionRegla definicion) {
        String union = definicion.combinacion() == DefinicionRegla.Combinacion.TODAS ? " y " : " o ";
        String condiciones = String.join(union, definicion.condiciones().stream().map(this::texto).toList());
        String texto = (definicion.combinacion() == DefinicionRegla.Combinacion.TODAS
                ? "Se activa cuando se cumplen todas: " : "Se activa cuando se cumple alguna: ") + condiciones;
        return texto.length() <= 500 ? texto : texto.substring(0, 497) + "...";
    }

    public FactorEvaluado evaluar(String codigo, String nombre, DefinicionRegla definicion,
                                  TransaccionEvaluable transaccion, double promedio) {
        List<String> cumplidas = new ArrayList<>();
        List<String> incumplidas = new ArrayList<>();
        for (Condicion condicion : definicion.condiciones()) {
            (cumple(condicion, transaccion, promedio) ? cumplidas : incumplidas).add(texto(condicion));
        }
        boolean disparada = definicion.combinacion() == DefinicionRegla.Combinacion.TODAS
                ? incumplidas.isEmpty() : !cumplidas.isEmpty();
        String motivo = disparada
                ? "Se cumple: " + String.join("; ", cumplidas)
                : "No se cumple: " + String.join("; ", incumplidas.isEmpty() ? cumplidas : incumplidas);
        return new FactorEvaluado(codigo.toLowerCase(Locale.ROOT), nombre, disparada ? 1 : 0, 1, motivo);
    }

    private boolean cumple(Condicion condicion, TransaccionEvaluable t, double promedio) {
        return switch (condicion.campo()) {
            case PAIS -> condicion.valor().equals(t.pais());
            case DISPOSITIVO -> condicion.valor().equals(t.dispositivo());
            case IP -> condicion.valor().equals(t.ip());
            case BENEFICIARIO -> condicion.valor().equals(t.beneficiario());
            case MONTO -> comparar(BigDecimal.valueOf(t.monto()), condicion);
            case HORA -> comparar(BigDecimal.valueOf(t.hora()), condicion);
            case OPERACIONES_ULTIMA_HORA -> comparar(BigDecimal.valueOf(t.operacionesUltimaHora()), condicion);
            // Sin promedio guardado no hay proporción que comparar: la condición no se cumple.
            case RATIO_PROMEDIO -> promedio > 0 && comparar(BigDecimal.valueOf(t.monto())
                    .divide(BigDecimal.valueOf(promedio), 6, RoundingMode.HALF_UP), condicion);
        };
    }

    private static boolean comparar(BigDecimal dato, Condicion condicion) {
        int orden = dato.compareTo(new BigDecimal(condicion.valor()));
        return switch (condicion.operador()) {
            case MAYOR -> orden > 0;
            case MAYOR_IGUAL -> orden >= 0;
            case MENOR -> orden < 0;
            case MENOR_IGUAL -> orden <= 0;
            case IGUAL -> orden == 0;
        };
    }

    private String texto(Condicion condicion) {
        String campo = switch (condicion.campo()) {
            case MONTO -> "Monto";
            case RATIO_PROMEDIO -> "Monto / promedio";
            case HORA -> "Hora";
            case OPERACIONES_ULTIMA_HORA -> "Operaciones última hora";
            case PAIS -> "País";
            case DISPOSITIVO -> "Dispositivo";
            case IP -> "IP";
            case BENEFICIARIO -> "Beneficiario";
        };
        String operador = switch (condicion.operador()) {
            case MAYOR -> ">";
            case MAYOR_IGUAL -> "≥";
            case MENOR -> "<";
            case MENOR_IGUAL -> "≤";
            case IGUAL -> "=";
        };
        return campo + " " + operador + " " + condicion.valor();
    }
}
