package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.ReglaFraude;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ReglaFraudeRepository extends JpaRepository<ReglaFraude, Integer> {
    List<ReglaFraude> findByEsActivaTrueOrderByCodigoReglaAsc();
    List<ReglaFraude> findAllByOrderByCodigoReglaAsc();
    Optional<ReglaFraude> findByCodigoRegla(String codigoRegla);

    /** Bloquea el catálogo completo para que dos cambios simultáneos no dejen el motor sin reglas activas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReglaFraude r order by r.codigoRegla")
    List<ReglaFraude> bloquearCatalogo();
}
