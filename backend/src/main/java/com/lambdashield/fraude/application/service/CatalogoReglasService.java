package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.domain.fraud.FactorRegla;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CatalogoReglasService {
    public record FactorConfigurado(FactorEvaluado factor, ReglaFraude regla) {}
    private final ReglaFraudeRepository repositorio;
    public CatalogoReglasService(ReglaFraudeRepository repositorio) { this.repositorio = repositorio; }

    public List<FactorConfigurado> aplicar(List<FactorEvaluado> factores) {
        Map<String, ReglaFraude> catalogo = repositorio.findAll().stream()
                .collect(Collectors.toMap(ReglaFraude::getCodigoRegla, Function.identity()));
        for (ReglaFraude regla : catalogo.values()) {
            if (regla.isEsActiva() && FactorRegla.porCodigo(regla.getCodigoRegla()).isEmpty()) {
                throw new IllegalStateException("Regla activa sin implementación: " + regla.getCodigoRegla());
            }
        }
        List<FactorConfigurado> resultado = new ArrayList<>();
        for (FactorEvaluado factor : factores) {
            String codigo = FactorRegla.porId(factor.id())
                    .orElseThrow(() -> new IllegalStateException("Factor desconocido: " + factor.id())).codigo();
            ReglaFraude regla = catalogo.get(codigo);
            if (regla == null) throw new IllegalStateException("Falta la regla del catálogo: " + codigo);
            if (!regla.isEsActiva()) continue;
            int maximo = pesoEntero(regla);
            int puntos = BigDecimal.valueOf(factor.puntaje()).multiply(BigDecimal.valueOf(maximo))
                    .divide(BigDecimal.valueOf(factor.max()), 0, RoundingMode.HALF_UP).intValueExact();
            resultado.add(new FactorConfigurado(new FactorEvaluado(factor.id(), regla.getNombreRegla(),
                    puntos, maximo, factor.motivo()), regla));
        }
        return resultado;
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
