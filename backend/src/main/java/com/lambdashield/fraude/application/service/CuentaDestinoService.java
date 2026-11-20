package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.customer.NombreClienteFormatter;
import com.lambdashield.fraude.domain.enums.EstadoCuenta;
import com.lambdashield.fraude.repository.CuentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class CuentaDestinoService {
    public record Destino(String nombre, String numeroCuenta, String banco) {}
    private final CuentaRepository cuentas;
    public CuentaDestinoService(CuentaRepository cuentas) { this.cuentas = cuentas; }
    @Transactional(readOnly = true)
    public Optional<Destino> buscar(String numero, String clienteOrigen) {
        if (clienteOrigen == null) return Optional.empty();
        return cuentas.findByNumeroCuenta(numero.replace(" ", ""))
                .filter(c -> c.getEstadoCuenta() == EstadoCuenta.ACTIVA && c.getCliente().isEsActivo()
                        && !c.getCliente().getCodigoCliente().equals(clienteOrigen))
                .map(c -> new Destino(NombreClienteFormatter.nombreCompleto(c.getCliente()), c.getNumeroCuenta(), "lambda"));
    }
}
