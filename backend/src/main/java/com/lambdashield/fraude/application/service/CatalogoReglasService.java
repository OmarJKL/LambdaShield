package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.FactorRegla;
import com.lambdashield.fraude.domain.fraud.TransaccionEvaluable;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Aplica el catálogo al motor. El peso de cada regla es relativo: su máximo es
 * peso ÷ suma de pesos participantes × 100, de modo que las reglas activas reparten
 * siempre el 100 % del puntaje. Activar, desactivar o agregar una regla reajusta al resto.
 */
@Service
public class CatalogoReglasService {
    public record FactorConfigurado(FactorEvaluado factor, ReglaFraude regla) {}

    /** Código reservado para una definición que se prueba sin guardar. */
    public static final String CODIGO_BORRADOR = "BORRADOR";

    private final ReglaFraudeRepository repositorio;
    private final DeteccionFraudeService deteccion;
    private final ReglaDefinidaEvaluador definidas;

    public CatalogoReglasService(ReglaFraudeRepository repositorio, DeteccionFraudeService deteccion,
                                 ReglaDefinidaEvaluador definidas) {
        this.repositorio = repositorio;
        this.deteccion = deteccion;
        this.definidas = definidas;
    }

    /** Evaluación real: solo participan las reglas activas. */
    public List<FactorConfigurado> evaluar(TransaccionEvaluable transaccion, double promedio) {
        return evaluar(transaccion, promedio, Set.of(), null);
    }

    /**
     * Evaluación de prueba: además de las activas participan las reglas candidatas indicadas
     * (aunque estén inactivas) y, si existe, un borrador sin guardar. No persiste nada.
     */
    public List<FactorConfigurado> evaluar(TransaccionEvaluable transaccion, double promedio,
                                           Set<String> candidatas, DefinicionRegla borrador) {
        Map<String, ReglaFraude> catalogo = repositorio.findAll().stream()
                .collect(Collectors.toMap(ReglaFraude::getCodigoRegla, Function.identity()));
        for (ReglaFraude regla : catalogo.values()) {
            if (regla.isEsActiva() && !implementada(regla)) {
                throw new IllegalStateException("Regla activa sin implementación: " + regla.getCodigoRegla());
            }
        }
        for (String codigo : candidatas) {
            ReglaFraude regla = catalogo.get(codigo);
            if (regla == null || !implementada(regla)) {
                throw new IllegalArgumentException("La regla candidata no existe o no se puede evaluar: " + codigo);
            }
            try { pesoEntero(regla); } catch (IllegalStateException ex) {
                throw new IllegalArgumentException("La regla candidata " + codigo + " tiene un peso inválido");
            }
        }

        // Factores sin escalar de todas las reglas participantes, en orden estable.
        Map<String, FactorConfigurado> crudos = new LinkedHashMap<>();
        for (FactorEvaluado factor : deteccion.evaluar(transaccion, promedio)) {
            String codigo = FactorRegla.porId(factor.id())
                    .orElseThrow(() -> new IllegalStateException("Factor desconocido: " + factor.id())).codigo();
            ReglaFraude regla = catalogo.get(codigo);
            if (regla == null) throw new IllegalStateException("Falta la regla del catálogo: " + codigo);
            if (regla.isEsActiva() || candidatas.contains(codigo)) crudos.put(codigo, new FactorConfigurado(factor, regla));
        }
        catalogo.values().stream()
                .filter(r -> FactorRegla.porCodigo(r.getCodigoRegla()).isEmpty())
                .filter(r -> r.isEsActiva() || candidatas.contains(r.getCodigoRegla()))
                .sorted(Comparator.comparing(ReglaFraude::getCodigoRegla))
                .forEach(r -> crudos.put(r.getCodigoRegla(), new FactorConfigurado(definidas.evaluar(r.getCodigoRegla(),
                        r.getNombreRegla(), definidas.leer(r.getParametrosJson()).orElseThrow(), transaccion, promedio), r)));
        if (borrador != null) {
            borrador.validar();
            ReglaFraude regla = ReglaFraude.builder().codigoRegla(CODIGO_BORRADOR).nombreRegla(borrador.nombre())
                    .peso(BigDecimal.valueOf(borrador.puntos())).versionRegla(0).esActiva(true).build();
            crudos.put(CODIGO_BORRADOR, new FactorConfigurado(
                    definidas.evaluar(CODIGO_BORRADOR, borrador.nombre(), borrador, transaccion, promedio), regla));
        }

        int suma = crudos.values().stream().mapToInt(c -> pesoEntero(c.regla())).sum();
        List<FactorConfigurado> resultado = new ArrayList<>();
        for (FactorConfigurado crudo : crudos.values()) {
            FactorEvaluado factor = crudo.factor();
            int peso = pesoEntero(crudo.regla());
            int maximo = suma == 0 ? 0 : redondear(BigDecimal.valueOf(peso * 100L), BigDecimal.valueOf(suma));
            // Se escala desde la fracción exacta, no desde el máximo ya redondeado.
            int puntos = suma == 0 || factor.max() == 0 ? 0 : redondear(
                    BigDecimal.valueOf((long) factor.puntaje() * peso * 100L),
                    BigDecimal.valueOf((long) factor.max() * suma));
            resultado.add(new FactorConfigurado(new FactorEvaluado(factor.id(), crudo.regla().getNombreRegla(),
                    puntos, maximo, factor.motivo()), crudo.regla()));
        }
        return resultado;
    }

    /** Regla con lógica disponible: implementación Java (RF) o definición tipada válida (RC). */
    public boolean implementada(ReglaFraude regla) {
        return FactorRegla.porCodigo(regla.getCodigoRegla()).isPresent()
                || definidas.leer(regla.getParametrosJson()).isPresent();
    }

    /** Participación porcentual de cada regla activa con peso válido, en el orden recibido. */
    public static Map<String, BigDecimal> participaciones(List<ReglaFraude> reglas) {
        Map<String, Integer> pesos = new LinkedHashMap<>();
        for (ReglaFraude regla : reglas) {
            if (!regla.isEsActiva()) continue;
            try { pesos.put(regla.getCodigoRegla(), pesoEntero(regla)); } catch (IllegalStateException ignorada) { }
        }
        int suma = pesos.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, BigDecimal> resultado = new LinkedHashMap<>();
        pesos.forEach((codigo, peso) -> resultado.put(codigo, suma == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(peso * 100L).divide(BigDecimal.valueOf(suma), 1, RoundingMode.HALF_UP)));
        return resultado;
    }

    private static int redondear(BigDecimal numerador, BigDecimal denominador) {
        return numerador.divide(denominador, 0, RoundingMode.HALF_UP).intValueExact();
    }

    static int pesoEntero(ReglaFraude regla) {
        try {
            int peso = regla.getPeso().intValueExact();
            if (peso < 0 || peso > 100) throw new ArithmeticException();
            return peso;
        } catch (ArithmeticException | NullPointerException ex) {
            throw new IllegalStateException("Peso inválido (entero 0-100): " + regla.getCodigoRegla(), ex);
        }
    }
}
