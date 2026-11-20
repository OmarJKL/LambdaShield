package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.EvaluacionRegla;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluacionReglaRepository extends JpaRepository<EvaluacionRegla, Long> {
    @EntityGraph(attributePaths = {"regla"})
    List<EvaluacionRegla> findByEvaluacion_IdEvaluacionInOrderByIdEvaluacionReglaAsc(List<Long> ids);
}
