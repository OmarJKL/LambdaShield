package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.DefinicionRegla.Campo;
import com.lambdashield.fraude.application.model.DefinicionRegla.Combinacion;
import com.lambdashield.fraude.application.model.DefinicionRegla.Condicion;
import com.lambdashield.fraude.application.model.DefinicionRegla.Operador;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglaDefinidaEvaluadorTest {
    private final ReglaDefinidaEvaluador evaluador = new ReglaDefinidaEvaluador();
    private final TransaccionEvaluable transaccion = new TransaccionEvaluable(300, 2, "regional", "nuevo", 3, "nuevo", "conocida");

    private static DefinicionRegla regla(Combinacion combinacion, Condicion... condiciones) {
        return new DefinicionRegla("Prueba", null, combinacion, 25, List.of(condiciones));
    }

    private boolean dispara(DefinicionRegla definicion, double promedio) {
        return evaluador.evaluar("RC001", "Prueba", definicion, transaccion, promedio).puntaje() == 1;
    }

    @Test
    void operadoresRespetanLaFronteraExacta() {
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.MONTO, Operador.MAYOR, "300")), 100)).isFalse();
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.MONTO, Operador.MAYOR_IGUAL, "300.00")), 100)).isTrue();
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.HORA, Operador.MENOR, "2")), 100)).isFalse();
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.HORA, Operador.MENOR_IGUAL, "2")), 100)).isTrue();
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.OPERACIONES_ULTIMA_HORA, Operador.IGUAL, "3")), 100)).isTrue();
        assertThat(dispara(regla(Combinacion.TODAS, new Condicion(Campo.RATIO_PROMEDIO, Operador.MAYOR_IGUAL, "3")), 100)).isTrue();
    }

    @Test
    void todasExigeCadaCondicionYCualquieraSoloUna() {
        Condicion pais = new Condicion(Campo.PAIS, Operador.IGUAL, "regional");
        Condicion ip = new Condicion(Campo.IP, Operador.IGUAL, "nueva");
        assertThat(dispara(regla(Combinacion.TODAS, pais, ip), 100)).isFalse();
        assertThat(dispara(regla(Combinacion.CUALQUIERA, pais, ip), 100)).isTrue();
        var factor = evaluador.evaluar("RC001", "Prueba", regla(Combinacion.CUALQUIERA, pais, ip), transaccion, 100);
        assertThat(factor.id()).isEqualTo("rc001");
        assertThat(factor.max()).isEqualTo(1);
        assertThat(factor.motivo()).isEqualTo("Se cumple: País = regional");
    }

    @Test
    void sinPromedioLaProporcionNoSeCumpleYLaDefinicionSeConservaAlSerializar() {
        var definicion = regla(Combinacion.TODAS, new Condicion(Campo.RATIO_PROMEDIO, Operador.MAYOR, "0"));
        assertThat(dispara(definicion, 0)).isFalse();
        assertThat(evaluador.leer(evaluador.serializar(definicion))).contains(definicion);
        assertThat(evaluador.leer("{\"nombre\":\"\"}")).isEmpty();
        assertThat(evaluador.leer("no es json")).isEmpty();
        assertThat(evaluador.resumen(definicion)).isEqualTo("Se activa cuando se cumplen todas: Monto / promedio > 0");
    }
}
