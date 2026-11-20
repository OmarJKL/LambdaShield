package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.Cuenta;
import com.lambdashield.fraude.domain.enums.NaturalezaTransaccion;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Se invoca dentro de la transacción que mantiene el bloqueo de cuenta. */
@Service
public class MovimientoSaldoService {
    public void aplicar(Cuenta cuenta, BigDecimal monto, NaturalezaTransaccion naturaleza, boolean reverso) {
        BigDecimal cambio = naturaleza == NaturalezaTransaccion.CREDITO ? monto : monto.negate();
        if (reverso) cambio = cambio.negate();
        BigDecimal saldo = cuenta.getSaldoDisponible().add(cambio);
        if (saldo.signum() < 0) {
            throw new SaldoInsuficienteException(cuenta.getNumeroCuenta(), cuenta.getSaldoDisponible(), cambio.abs());
        }
        cuenta.setSaldoDisponible(saldo);
    }
}
