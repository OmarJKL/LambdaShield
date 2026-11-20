package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Transaccion;
import com.lambdashield.fraude.domain.enums.NaturalezaTransaccion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {
    boolean existsByCodigoTransaccion(java.util.UUID codigoTransaccion);
    boolean existsByCuenta_Cliente_CodigoClienteAndDireccionIp(String codigoCliente, String direccionIp);

    interface TotalNaturaleza {
        NaturalezaTransaccion getNaturaleza();
        BigDecimal getMonto();
    }

    @Query("""
            select t.tipoTransaccion.naturaleza as naturaleza, sum(t.montoTransaccion) as monto
            from Transaccion t
            where t.fechaHoraTransaccion >= :desde and t.fechaHoraTransaccion < :hasta
              and t.estadoTransaccion = com.lambdashield.fraude.domain.enums.EstadoTransaccion.APROBADA
            group by t.tipoTransaccion.naturaleza
            """)
    List<TotalNaturaleza> sumarMontosPorNaturaleza(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);

    @Query("""
            select t from Transaccion t
            join fetch t.cuenta c
            join fetch c.cliente
            join fetch t.tipoTransaccion
            where t.fechaHoraTransaccion >= :desde and t.fechaHoraTransaccion < :hasta
              and t.estadoTransaccion = com.lambdashield.fraude.domain.enums.EstadoTransaccion.APROBADA
            order by t.fechaHoraTransaccion desc, t.idTransaccion desc
            """)
    List<Transaccion> buscarDelPeriodo(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta, Pageable pageable);
}
