package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.ResumenFinanciero;
import com.lambdashield.fraude.domain.enums.EstadoCuenta;
import com.lambdashield.fraude.domain.enums.NaturalezaTransaccion;
import com.lambdashield.fraude.repository.CuentaRepository;
import com.lambdashield.fraude.repository.TransaccionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardResumenService {
    private final TransaccionRepository transacciones;
    private final CuentaRepository cuentas;
    public DashboardResumenService(TransaccionRepository transacciones, CuentaRepository cuentas) {
        this.transacciones = transacciones;
        this.cuentas = cuentas;
    }
    @Transactional(readOnly = true)
    public ResumenFinanciero obtenerResumen() { return obtenerResumen(365); }

    @Transactional(readOnly = true)
    public ResumenFinanciero obtenerResumen(int dias) {
        PeriodoConsulta periodo = PeriodoConsulta.ultimosDias(dias);
        Map<NaturalezaTransaccion, BigDecimal> actual = totales(periodo);
        Map<NaturalezaTransaccion, BigDecimal> anterior = totales(periodo.anterior());
        BigDecimal ingresos = actual.getOrDefault(NaturalezaTransaccion.CREDITO, BigDecimal.ZERO);
        BigDecimal egresos = actual.getOrDefault(NaturalezaTransaccion.DEBITO, BigDecimal.ZERO);
        BigDecimal saldo = cuentas.sumarSaldoPorEstado(EstadoCuenta.ACTIVA);
        return new ResumenFinanciero(saldo, ingresos, egresos,
                variacionPorcentual(saldo, saldo.subtract(ingresos).add(egresos)),
                variacionPorcentual(ingresos, anterior.getOrDefault(NaturalezaTransaccion.CREDITO, BigDecimal.ZERO)),
                variacionPorcentual(egresos, anterior.getOrDefault(NaturalezaTransaccion.DEBITO, BigDecimal.ZERO)));
    }
    private Map<NaturalezaTransaccion, BigDecimal> totales(PeriodoConsulta periodo) {
        Map<NaturalezaTransaccion, BigDecimal> resultado = new EnumMap<>(NaturalezaTransaccion.class);
        transacciones.sumarMontosPorNaturaleza(periodo.desde(), periodo.hasta())
                .forEach(total -> resultado.put(total.getNaturaleza(), total.getMonto()));
        return resultado;
    }
    static double variacionPorcentual(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) return actual.signum() == 0 ? 0 : 100;
        return actual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
