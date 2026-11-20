package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.device.DeviceFingerprint;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.entity.Dispositivo;
import com.lambdashield.fraude.domain.enums.TipoDispositivo;
import com.lambdashield.fraude.repository.DispositivoRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DispositivoService {
    private final DispositivoRepository repositorio;
    public DispositivoService(DispositivoRepository repositorio) { this.repositorio = repositorio; }

    private Optional<Dispositivo> buscar(String huella) {
        return repositorio.findByHuellaDispositivo(huella).or(() -> repositorio
                .findFirstByHuellaDispositivoStartingWithOrderByIdDispositivoAsc(huella + "|"));
    }
    public boolean esConocido(String codigoCliente, String userAgent) {
        if (codigoCliente == null || userAgent == null || userAgent.isBlank()) return false;
        return buscar(DeviceFingerprint.from(codigoCliente, DeviceFingerprint.normalize(userAgent)))
                .map(Dispositivo::isEsConocido).orElse(false);
    }
    public Dispositivo resolver(Cliente cliente, String tipoSolicitado, String userAgent) {
        String normalizado = userAgent != null ? DeviceFingerprint.normalize(userAgent)
                : "Simulador|" + ("reconocido".equals(tipoSolicitado) ? "Reconocido" : "Nuevo");
        String huella = DeviceFingerprint.from(cliente.getCodigoCliente(), normalizado);
        OffsetDateTime ahora = OffsetDateTime.now();
        Dispositivo dispositivo = buscar(huella).orElseGet(() -> {
            String[] partes = normalizado.split("\\|", 2);
            String sistema = partes[1];
            return Dispositivo.builder().cliente(cliente).huellaDispositivo(huella)
                    .navegador(partes[0]).sistemaOperativo(sistema)
                    .tipoDispositivo("Android".equals(sistema) || "iOS".equals(sistema) ? TipoDispositivo.MOVIL : TipoDispositivo.PC)
                    .esConocido(userAgent == null && "reconocido".equals(tipoSolicitado))
                    .fechaPrimerUso(ahora).build();
        });
        // Las huellas antiguas con UUID se normalizan al volver a usarse.
        dispositivo.setHuellaDispositivo(huella);
        dispositivo.setFechaUltimoUso(ahora);
        return repositorio.save(dispositivo);
    }
}
