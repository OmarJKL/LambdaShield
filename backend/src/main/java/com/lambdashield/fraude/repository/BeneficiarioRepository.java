package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Beneficiario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficiarioRepository extends JpaRepository<Beneficiario, Long> {
    Optional<Beneficiario> findFirstByCliente_IdClienteAndNumeroCuentaDestino(Long idCliente, String numero);
    List<Beneficiario> findByCliente_CodigoClienteAndEsActivoTrueAndNumeroCuentaDestinoIsNotNullOrderByNombreBeneficiarioAsc(String cliente);
    Optional<Beneficiario> findFirstByCliente_IdClienteAndNombreBeneficiario(Long idCliente, String nombreBeneficiario);

    /** Contacto del cliente con la misma cuenta, aunque se haya escrito con otro formato. */
    @Query("""
            select b from Beneficiario b where b.cliente.idCliente = :idCliente
            and replace(replace(b.numeroCuentaDestino, '-', ''), ' ', '') = :digitos order by b.idBeneficiario""")
    List<Beneficiario> buscarPorCuentaNormalizada(@Param("idCliente") Long idCliente, @Param("digitos") String digitos);
}
