package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Cuenta;
import com.lambdashield.fraude.domain.enums.EstadoCuenta;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.idCuenta = :id")
    Optional<Cuenta> bloquearPorId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cuenta> findFirstByCliente_IdClienteOrderByIdCuentaAsc(Long idCliente);

    Optional<Cuenta> findTopByCliente_IdClienteOrderByIdCuentaAsc(Long idCliente);

    @Query("select coalesce(sum(c.saldoDisponible), 0) from Cuenta c where c.estadoCuenta = :estado")
    BigDecimal sumarSaldoPorEstado(@Param("estado") EstadoCuenta estado);
}
