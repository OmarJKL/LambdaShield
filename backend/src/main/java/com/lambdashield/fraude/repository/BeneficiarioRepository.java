package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Beneficiario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiarioRepository extends JpaRepository<Beneficiario, Long> {
    Optional<Beneficiario> findFirstByCliente_IdClienteAndNumeroCuentaDestino(Long idCliente, String numero);
    java.util.List<Beneficiario> findByCliente_CodigoClienteAndEsActivoTrueAndNumeroCuentaDestinoIsNotNullOrderByNombreBeneficiarioAsc(String cliente);
    Optional<Beneficiario> findFirstByCliente_IdClienteAndNombreBeneficiario(Long idCliente, String nombreBeneficiario);
}
