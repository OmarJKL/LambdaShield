package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.domain.account.NumeroCuenta;
import com.lambdashield.fraude.domain.entity.Beneficiario;
import com.lambdashield.fraude.domain.entity.Canal;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.entity.Dispositivo;
import com.lambdashield.fraude.domain.entity.TipoTransaccion;
import com.lambdashield.fraude.domain.enums.NaturalezaTransaccion;
import com.lambdashield.fraude.repository.BeneficiarioRepository;
import com.lambdashield.fraude.repository.CanalRepository;
import com.lambdashield.fraude.repository.TipoTransaccionRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Resuelve y crea las entidades de catálogo asociadas a una transacción. */
@Component
public class TransaccionReferenciasResolver {

    private final TipoTransaccionRepository tipoTransaccionRepository;
    private final CanalRepository canalRepository;
    private final DispositivoService dispositivos;
    private final BeneficiarioRepository beneficiarioRepository;
    private final com.lambdashield.fraude.repository.CuentaRepository cuentas;

    public TransaccionReferenciasResolver(TipoTransaccionRepository tipoTransaccionRepository,
                                          CanalRepository canalRepository,
                                          DispositivoService dispositivos,
                                          BeneficiarioRepository beneficiarioRepository, com.lambdashield.fraude.repository.CuentaRepository cuentas) {
        this.tipoTransaccionRepository = tipoTransaccionRepository;
        this.canalRepository = canalRepository;
        this.dispositivos = dispositivos;
        this.beneficiarioRepository = beneficiarioRepository;
        this.cuentas = cuentas;
    }

    /**
     * @param catalogoAbierto true solo para las operaciones administrativas (simulador y carga demo),
     *                        que pueden registrar una categoría nueva. El portal del cliente usa el catálogo existente.
     */
    public Referencias resolver(Cliente cliente, EvaluarTransaccionCommand request, String userAgent, boolean catalogoAbierto) {
        return new Referencias(
                tipoTransaccion(request.categoria(), catalogoAbierto),
                canalWeb(),
                dispositivos.resolver(cliente, request.dispositivo(), userAgent),
                request.numeroCuentaDestino() != null && !request.numeroCuentaDestino().isBlank()
                        ? beneficiarioPorCuenta(cliente, request)
                        : beneficiario(cliente, request.beneficiario(), request.beneficiarioNombre())
        );
    }

    private TipoTransaccion tipoTransaccion(String categoria, boolean catalogoAbierto) {
        String codigo = categoria == null || categoria.isBlank()
                ? "otros"
                : categoria.trim().toLowerCase(Locale.ROOT);
        if (!catalogoAbierto) {
            return tipoTransaccionRepository.findByCodigoTipo(codigo).filter(TipoTransaccion::isEsActivo)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no está disponible"));
        }
        return tipoTransaccionRepository.findByCodigoTipo(codigo)
                .orElseGet(() -> tipoTransaccionRepository.save(TipoTransaccion.builder()
                        .codigoTipo(codigo)
                        .nombreTipo(capitalizar(codigo))
                        .naturaleza(NaturalezaTransaccion.DEBITO)
                        .esActivo(true)
                        .build()));
    }

    private Canal canalWeb() {
        return canalRepository.findByCodigoCanal("WEB")
                .orElseGet(() -> canalRepository.save(Canal.builder()
                        .codigoCanal("WEB")
                        .nombreCanal("Banca por internet")
                        .esActivo(true)
                        .build()));
    }

    /**
     * Resuelve el beneficiario de la operación. Si se conoce su nombre, el tipo
     * (nuevo o recurrente) se deriva del historial real del cliente: la primera
     * vez se crea como nuevo y en las siguientes transferencias al mismo
     * destinatario pasa a recurrente. Sin nombre, se respeta el tipo declarado
     * con un destinatario genérico (simulador del administrador).
     */
    private Beneficiario beneficiario(Cliente cliente, String tipoBeneficiario, String nombreBeneficiario) {
        if (nombreBeneficiario != null && !nombreBeneficiario.isBlank()) {
            String nombre = nombreBeneficiario.trim();
            return beneficiarioRepository
                    .findFirstByCliente_IdClienteAndNombreBeneficiario(cliente.getIdCliente(), nombre)
                    .map(existente -> {
                        comprobarActivo(existente);
                        existente.setEsConocido(true);
                        existente.setFechaUltimoUso(OffsetDateTime.now());
                        return beneficiarioRepository.save(existente);
                    })
                    .orElseGet(() -> crearBeneficiarioSinCuenta(cliente, nombre, false));
        }

        boolean nuevo = "nuevo".equalsIgnoreCase(tipoBeneficiario);
        String nombre = nuevo ? "Destinatario nuevo" : "Destinatario recurrente";
        return beneficiarioRepository
                .findFirstByCliente_IdClienteAndNombreBeneficiario(cliente.getIdCliente(), nombre)
                .map(TransaccionReferenciasResolver::comprobarActivo)
                .orElseGet(() -> crearBeneficiarioSinCuenta(cliente, nombre, !nuevo));
    }

    private Beneficiario crearBeneficiarioSinCuenta(Cliente cliente, String nombre, boolean conocido) {
        OffsetDateTime ahora = OffsetDateTime.now();
        return beneficiarioRepository.save(Beneficiario.builder().cliente(cliente).nombreBeneficiario(nombre)
                .entidadFinanciera("Lambda Shield").codigoPais("PE").esConocido(conocido)
                .fechaPrimerUso(ahora).fechaUltimoUso(ahora).esActivo(true).build());
    }
    private static Beneficiario comprobarActivo(Beneficiario beneficiario) {
        if (!beneficiario.isEsActivo()) throw new IllegalArgumentException("El destinatario está desactivado");
        return beneficiario;
    }

    private Beneficiario beneficiarioPorCuenta(Cliente cliente, EvaluarTransaccionCommand request) {
        String numero = request.numeroCuentaDestino().replace(" ", "").trim();
        String digitos = NumeroCuenta.normalizar(numero);
        var destino = cuentas.buscarPorNumeroNormalizado(digitos);
        if (destino.isEmpty() && "lambda".equals(request.bancoDestino())) {
            throw new IllegalArgumentException("No existe una cuenta Lambda Shield con ese número");
        }
        if (destino.isPresent() && (destino.get().getEstadoCuenta() != com.lambdashield.fraude.domain.enums.EstadoCuenta.ACTIVA
                || !destino.get().getCliente().isEsActivo())) {
            throw new IllegalArgumentException("La cuenta de destino no está disponible");
        }
        if (destino.isPresent() && destino.get().getCliente().getIdCliente().equals(cliente.getIdCliente())) {
            throw new IllegalArgumentException("Selecciona una cuenta de otro destinatario");
        }
        var existente = beneficiarioRepository.buscarPorCuentaNormalizada(cliente.getIdCliente(), digitos).stream().findFirst();
        if (existente.isPresent()) {
            Beneficiario beneficiario = existente.get();
            comprobarActivo(beneficiario);
            destino.ifPresent(c -> {
                beneficiario.setNombreBeneficiario(com.lambdashield.fraude.domain.customer.NombreClienteFormatter.nombreCompleto(c.getCliente()));
                beneficiario.setEntidadFinanciera("Lambda Shield");
            });
            beneficiario.setFechaUltimoUso(OffsetDateTime.now());
            beneficiario.setEsConocido(true);
            return beneficiario;
        }
        String nombre = destino.map(c -> com.lambdashield.fraude.domain.customer.NombreClienteFormatter.nombreCompleto(c.getCliente()))
                .orElse(request.beneficiarioNombre());
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre del destinatario es obligatorio");
        String banco = destino.isPresent() ? "Lambda Shield" : switch (request.bancoDestino() == null ? "" : request.bancoDestino()) {
            case "bcp" -> "BCP";
            case "bbva" -> "BBVA";
            case "interbank" -> "Interbank";
            case "scotiabank" -> "Scotiabank";
            default -> "Lambda Shield";
        };
        return beneficiarioRepository.save(Beneficiario.builder().cliente(cliente).nombreBeneficiario(nombre.trim())
                .numeroCuentaDestino(destino.map(com.lambdashield.fraude.domain.entity.Cuenta::getNumeroCuenta).orElse(numero))
                .entidadFinanciera(banco).codigoPais("PE").esConocido(false).esActivo(true)
                .fechaPrimerUso(OffsetDateTime.now()).fechaUltimoUso(OffsetDateTime.now()).build());
    }

    private static String capitalizar(String texto) {
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }

    public record Referencias(TipoTransaccion tipoTransaccion, Canal canal,
                              Dispositivo dispositivo, Beneficiario beneficiario) {}
}
