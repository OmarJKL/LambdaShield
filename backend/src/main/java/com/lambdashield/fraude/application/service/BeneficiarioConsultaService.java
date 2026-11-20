package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.repository.BeneficiarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BeneficiarioConsultaService {
    public record Contacto(Long id, String nombre, String numeroCuenta, String banco) {}
    private final BeneficiarioRepository repositorio;
    public BeneficiarioConsultaService(BeneficiarioRepository repositorio) { this.repositorio = repositorio; }
    @Transactional(readOnly = true)
    public List<Contacto> listar(String codigoCliente) {
        if (codigoCliente == null) return List.of();
        return repositorio.findByCliente_CodigoClienteAndEsActivoTrueAndNumeroCuentaDestinoIsNotNullOrderByNombreBeneficiarioAsc(codigoCliente)
                .stream().map(b -> new Contacto(b.getIdBeneficiario(), b.getNombreBeneficiario(), b.getNumeroCuentaDestino(), b.getEntidadFinanciera())).toList();
    }
}
