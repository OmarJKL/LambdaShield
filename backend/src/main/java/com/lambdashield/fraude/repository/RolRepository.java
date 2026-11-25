package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RolRepository extends JpaRepository<Rol, Short> {
    Optional<Rol> findByCodigoRol(String codigoRol);

    /** Fila común de coordinación para altas, cambios de rol/estado y bajas de usuarios. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rol r where r.codigoRol = 'ADMIN'")
    Optional<Rol> bloquearAdministracion();
}
