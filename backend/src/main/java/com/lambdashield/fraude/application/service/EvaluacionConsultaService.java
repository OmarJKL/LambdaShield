package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.mapper.EvaluacionResultadoMapper;
import com.lambdashield.fraude.application.model.EvaluacionResultado;
import com.lambdashield.fraude.application.model.FactorResultado;
import com.lambdashield.fraude.domain.entity.EvaluacionFraude;
import com.lambdashield.fraude.repository.EvaluacionFraudeRepository;
import com.lambdashield.fraude.repository.EvaluacionReglaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de consulta del historial de evaluaciones. */
@Service
public class EvaluacionConsultaService {

    private final EvaluacionFraudeRepository evaluacionRepository;
    private final EvaluacionReglaRepository evaluacionReglaRepository;
    private final EvaluacionResultadoMapper mapper;

    public EvaluacionConsultaService(EvaluacionFraudeRepository evaluacionRepository,
                                     EvaluacionReglaRepository evaluacionReglaRepository,
                                     EvaluacionResultadoMapper mapper) {
        this.evaluacionRepository = evaluacionRepository;
        this.evaluacionReglaRepository = evaluacionReglaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EvaluacionResultado> listar(boolean soloActivas) {
        return consultar(soloActivas, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<EvaluacionResultado> listarPorClienteCodigo(String codigoCliente) {
        return consultar(true, null, null, null, codigoCliente);
    }

    @Transactional(readOnly = true)
    public List<EvaluacionResultado> listarFiltrado(LocalDate desde, LocalDate hasta, String nivel, String codigoCliente) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a hasta");
        }
        return consultar(true, desde, hasta, nivel, codigoCliente);
    }

    private List<EvaluacionResultado> consultar(boolean soloActivas, LocalDate desde, LocalDate hasta,
                                               String nivel, String codigoCliente) {
        Specification<EvaluacionFraude> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (soloActivas) condiciones.add(cb.isTrue(raiz.get("esActiva")));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaEvaluacion"),
                    desde.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()));
            if (hasta != null) condiciones.add(cb.lessThan(raiz.get("fechaEvaluacion"),
                    hasta.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()));
            if (nivel != null && !nivel.isBlank()) condiciones.add(cb.equal(raiz.get("nivelRiesgo"),
                    com.lambdashield.fraude.domain.enums.NivelRiesgo.valueOf(nivel.toUpperCase(Locale.ROOT))));
            if (codigoCliente != null && !codigoCliente.isBlank()) condiciones.add(cb.equal(
                    raiz.get("transaccion").get("cuenta").get("cliente").get("codigoCliente"), codigoCliente));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        List<EvaluacionFraude> evaluaciones = evaluacionRepository.findAll(filtro,
                Sort.by(Direction.DESC, "fechaEvaluacion", "idEvaluacion"));
        if (evaluaciones.isEmpty()) return List.of();
        Map<Long, List<FactorResultado>> factores = evaluacionReglaRepository
                .findByEvaluacion_IdEvaluacionInOrderByIdEvaluacionReglaAsc(evaluaciones.stream()
                        .map(EvaluacionFraude::getIdEvaluacion).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getEvaluacion().getIdEvaluacion(),
                        Collectors.mapping(mapper::toFactor, Collectors.toList())));
        return evaluaciones.stream().map(e -> mapper.toResultado(e,
                factores.getOrDefault(e.getIdEvaluacion(), List.of()))).toList();
    }
}
