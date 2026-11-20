package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.Dispositivo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {
    List<Dispositivo> findByCliente_IdCliente(Long idCliente);
    Optional<Dispositivo> findByHuellaDispositivo(String huella);
    Optional<Dispositivo> findFirstByHuellaDispositivoStartingWithOrderByIdDispositivoAsc(String prefijo);
    boolean existsByCliente_IdClienteAndEsConocidoTrueAndDireccionIp(Long idCliente, String direccionIp);
}
