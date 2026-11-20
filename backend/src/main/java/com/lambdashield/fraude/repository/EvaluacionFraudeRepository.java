package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.EvaluacionFraude;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluacionFraudeRepository extends JpaRepository<EvaluacionFraude, Long>, JpaSpecificationExecutor<EvaluacionFraude> {
    List<EvaluacionFraude> findByEsActivaTrueOrderByFechaEvaluacionDesc();

    @Override
    @EntityGraph(attributePaths = {"transaccion", "transaccion.cuenta", "transaccion.cuenta.cliente", "transaccion.tipoTransaccion", "transaccion.caracteristica"})
    List<EvaluacionFraude> findAll(Specification<EvaluacionFraude> filtro, Sort orden);

    interface Metricas {
        long getTotal();
        long getAprobadas();
        long getMonitoreadas();
        long getEnRevision();
        long getBloqueadas();
        Double getPromedio();
    }
    @Query("""
            select count(e) as total,
                coalesce(sum(case when e.nivelRiesgo = com.lambdashield.fraude.domain.enums.NivelRiesgo.BAJO then 1 else 0 end), 0) as aprobadas,
                coalesce(sum(case when e.nivelRiesgo = com.lambdashield.fraude.domain.enums.NivelRiesgo.MEDIO then 1 else 0 end), 0) as monitoreadas,
                coalesce(sum(case when e.nivelRiesgo = com.lambdashield.fraude.domain.enums.NivelRiesgo.ALTO then 1 else 0 end), 0) as enRevision,
                coalesce(sum(case when e.nivelRiesgo = com.lambdashield.fraude.domain.enums.NivelRiesgo.CRITICO then 1 else 0 end), 0) as bloqueadas,
                avg(e.fraudeScore) as promedio
            from EvaluacionFraude e where e.esActiva = true
            """)
    Metricas calcularMetricas();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EvaluacionFraude e where e.idEvaluacion = :id")
    Optional<EvaluacionFraude> bloquearPorId(@Param("id") Long id);
    long countByEsActivaTrueAndTransaccion_Cuenta_Cliente_CodigoClienteAndFechaEvaluacionAfter(
            String codigoCliente, OffsetDateTime fecha);
}
