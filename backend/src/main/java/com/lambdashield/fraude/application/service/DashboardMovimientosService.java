package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.MovimientoDiario;
import com.lambdashield.fraude.application.model.MovimientoReciente;
import com.lambdashield.fraude.domain.entity.Transaccion;
import com.lambdashield.fraude.domain.enums.NaturalezaTransaccion;
import com.lambdashield.fraude.repository.TransaccionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provee los movimientos reales del periodo usados por las gráficas y tablas
 * del dashboard: serie diaria de ingresos/egresos y últimos movimientos.
 */
@Service
public class DashboardMovimientosService {

    private final TransaccionRepository transaccionRepository;

    public DashboardMovimientosService(TransaccionRepository transaccionRepository) {
        this.transaccionRepository = transaccionRepository;
    }

    @Transactional(readOnly = true)
    public List<MovimientoDiario> generarMovimientos(int dias) {
        PeriodoConsulta periodo = PeriodoConsulta.ultimosDias(dias);
        LocalDate hoy = periodo.fin();
        LocalDate desdeFecha = periodo.inicio();
        List<Transaccion> transacciones = transaccionRepository.buscarDelPeriodo(periodo.desde(), periodo.hasta(), Pageable.unpaged());

        Map<LocalDate, BigDecimal[]> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desdeFecha; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            porDia.put(dia, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Transaccion transaccion : transacciones) {
            LocalDate dia = transaccion.getFechaHoraTransaccion().toLocalDate();
            BigDecimal[] acumulado = porDia.get(dia);
            if (acumulado == null) {
                continue;
            }
            if (transaccion.getTipoTransaccion().getNaturaleza() == NaturalezaTransaccion.CREDITO) {
                acumulado[0] = acumulado[0].add(transaccion.getMontoTransaccion());
            } else {
                acumulado[1] = acumulado[1].add(transaccion.getMontoTransaccion());
            }
        }

        DateTimeFormatter formato = dias <= 30
                ? DateTimeFormatter.ofPattern("dd/MM")
                : DateTimeFormatter.ofPattern("dd/MM/yy");
        List<MovimientoDiario> serie = new ArrayList<>();
        porDia.forEach((dia, totales) -> serie.add(new MovimientoDiario(
                dia.format(formato), totales[0], totales[1])));
        return serie;
    }

    @Transactional(readOnly = true)
    public List<MovimientoReciente> obtenerMovimientosRecientes(int cantidad) {
        return obtenerMovimientosRecientes(365, cantidad);
    }

    @Transactional(readOnly = true)
    public List<MovimientoReciente> obtenerMovimientosRecientes(int dias, int cantidad) {
        PeriodoConsulta periodo = PeriodoConsulta.ultimosDias(dias);
        List<Transaccion> transacciones = transaccionRepository.buscarDelPeriodo(
                periodo.desde(), periodo.hasta(), PageRequest.of(0, cantidad));
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return transacciones.stream()
                .map(transaccion -> new MovimientoReciente(
                        transaccion.getFechaHoraTransaccion().format(formato),
                        transaccion.getTipoTransaccion().getNombreTipo() + " · "
                                + transaccion.getCuenta().getCliente().getCodigoCliente(),
                        transaccion.getTipoTransaccion().getNaturaleza() == NaturalezaTransaccion.CREDITO,
                        transaccion.getMontoTransaccion()
                ))
                .toList();
    }

}
