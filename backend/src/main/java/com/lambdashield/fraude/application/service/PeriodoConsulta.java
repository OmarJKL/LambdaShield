package com.lambdashield.fraude.application.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** El rango incluye hoy y los días anteriores indicados por el contrato del dashboard. */
record PeriodoConsulta(LocalDate inicio, LocalDate fin) {
    static PeriodoConsulta ultimosDias(int dias) {
        LocalDate hoy = LocalDate.now();
        return new PeriodoConsulta(hoy.minusDays(dias), hoy);
    }
    OffsetDateTime desde() { return inicio.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(); }
    OffsetDateTime hasta() { return fin.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(); }
    PeriodoConsulta anterior() {
        long cantidad = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        return new PeriodoConsulta(inicio.minusDays(cantidad), inicio.minusDays(1));
    }
}
