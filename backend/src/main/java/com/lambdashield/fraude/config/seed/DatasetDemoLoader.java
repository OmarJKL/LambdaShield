package com.lambdashield.fraude.config.seed;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.service.EvaluacionTransaccionService;
import com.lambdashield.fraude.domain.device.DeviceFingerprint;
import com.lambdashield.fraude.domain.entity.*;
import com.lambdashield.fraude.domain.enums.*;
import com.lambdashield.fraude.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Carga aditiva y repetible: ocho cuentas y 5252 evaluaciones históricas, sin reiniciar saldos. */
@Service
public class DatasetDemoLoader {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final CatalogoDemoSeeder catalogos;
    private final ClienteRepository clientes;
    private final CuentaRepository cuentas;
    private final UsuarioSistemaRepository usuarios;
    private final UsuarioRolRepository asignaciones;
    private final RolRepository roles;
    private final DispositivoRepository dispositivos;
    private final TipoTransaccionRepository tipos;
    private final TransaccionRepository transacciones;
    private final EvaluacionFraudeRepository evaluaciones;
    private final EvaluacionTransaccionService operaciones;
    private final PasswordEncoder encoder;
    private final BeneficiarioRepository beneficiarios;

    public DatasetDemoLoader(CatalogoDemoSeeder catalogos, ClienteRepository clientes, CuentaRepository cuentas,
            UsuarioSistemaRepository usuarios, UsuarioRolRepository asignaciones, RolRepository roles,
            DispositivoRepository dispositivos, TipoTransaccionRepository tipos, TransaccionRepository transacciones,
            EvaluacionFraudeRepository evaluaciones, EvaluacionTransaccionService operaciones, PasswordEncoder encoder, BeneficiarioRepository beneficiarios) {
        this.catalogos = catalogos;
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.roles = roles;
        this.dispositivos = dispositivos;
        this.tipos = tipos;
        this.transacciones = transacciones;
        this.evaluaciones = evaluaciones;
        this.operaciones = operaciones;
        this.encoder = encoder;
        this.beneficiarios = beneficiarios;
    }

    public record Resumen(int clientes, int cuentas, int usuarios, int transacciones) {}
    private record Movimiento(int dias, int hora, String categoria, BigDecimal monto,
                              String pais, String dispositivo, int frecuencia, String beneficiario, String ip) {}

    @Transactional
    public Resumen cargar() {
        catalogos.seedRoles();
        catalogos.seedCatalogos();
        catalogos.seedReglas();
        crearTipo("demo_apertura", "Depósito de apertura", NaturalezaTransaccion.CREDITO);
        crearTipo("demo_haberes", "Abono de haberes", NaturalezaTransaccion.CREDITO);
        crearTipo("demo_reembolso", "Reembolso recibido", NaturalezaTransaccion.CREDITO);
        crearTipo("demo_abono", "Abono adicional", NaturalezaTransaccion.CREDITO);
        crearTipo("transferencia", "Transferencia", NaturalezaTransaccion.DEBITO);
        usuario(DatasetDemo.ADMIN, DatasetDemo.CLAVE_ADMIN, "Administrador de demostración", "ADMIN", null);
        usuario(DatasetDemo.ANALISTA, DatasetDemo.CLAVE_ANALISTA, "Analista de demostración", "ANALISTA", null);
        int nuevos = 0;
        OffsetDateTime ahora = OffsetDateTime.now();
        for (DatasetDemo.Persona persona : DatasetDemo.PERSONAS) {
            Cliente cliente = cliente(persona);
            cuenta(persona, cliente);
            conocidos(cliente, ahora);
            usuario(persona.usuario(), persona.clave(), persona.nombreCompleto(), "CLIENTE", cliente.getIdCliente());
            List<Movimiento> movimientos = movimientos(persona);
            for (int indice = 0; indice < movimientos.size(); indice++) {
                UUID codigo = codigoOperacion(persona.codigo(), indice);
                if (transacciones.existsByCodigoTransaccion(codigo)) continue;
                Movimiento movimiento = movimientos.get(indice);
                var resultado = operaciones.crearYEvaluar(new EvaluarTransaccionCommand(persona.codigo(),
                        movimiento.monto(), movimiento.categoria(), movimiento.hora(), movimiento.pais(),
                        movimiento.dispositivo(), movimiento.frecuencia(), movimiento.beneficiario(), movimiento.ip(), null));
                EvaluacionFraude evaluacion = evaluaciones.findById(resultado.idInterno()).orElseThrow();
                Transaccion transaccion = evaluacion.getTransaccion();
                // Los ingresos deben quedar confirmados; un catálogo distinto no puede dejar cuentas sin fondos.
                if (transaccion.getTipoTransaccion().getNaturaleza() == NaturalezaTransaccion.CREDITO
                        && transaccion.getEstadoTransaccion() != EstadoTransaccion.APROBADA) {
                    throw new IllegalStateException("El catálogo actual retiene ingresos demo; no se ha completado la carga");
                }
                OffsetDateTime fecha = ahora.minusDays(movimiento.dias()).withHour(movimiento.hora())
                        .withMinute(15).withSecond(0).withNano(0);
                if (fecha.isAfter(ahora)) fecha = ahora.withSecond(0).withNano(0);
                transaccion.setCodigoTransaccion(codigo);
                transaccion.setFechaHoraTransaccion(fecha);
                transaccion.setFechaRegistro(fecha);
                transaccion.setDireccionIp("nueva".equals(movimiento.ip()) ? "198.51.100." + (persona.numero() - 100) : "127.0.0.1");
                transaccion.setCiudad("regional".equals(movimiento.pais()) ? "Santiago" : "alto_riesgo".equals(movimiento.pais()) ? "No determinada" : "Lima");
                evaluacion.setFechaEvaluacion(fecha.plusSeconds(2));
                nuevos++;
                // Acota el contexto JPA al cargar miles de operaciones, sin fragmentar la transacción.
                if (nuevos % 100 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
        }
        for (DatasetDemo.Persona origen : DatasetDemo.PERSONAS) {
            Cliente titular = clientes.findByCodigoCliente(origen.codigo()).orElseThrow();
            for (DatasetDemo.Persona destino : DatasetDemo.PERSONAS) {
                if (origen.numero() == destino.numero()) continue;
                if (beneficiarios.findFirstByCliente_IdClienteAndNumeroCuentaDestino(titular.getIdCliente(), destino.cuenta()).isPresent()) continue;
                beneficiarios.save(Beneficiario.builder().cliente(titular).nombreBeneficiario(destino.nombreCompleto())
                        .numeroCuentaDestino(destino.cuenta()).entidadFinanciera("Lambda Shield").codigoPais("PE")
                        .esActivo(true).esConocido(true).fechaPrimerUso(ahora.minusDays(40)).fechaUltimoUso(ahora).build());
            }
        }
        return new Resumen(8, 8, 10, nuevos);
    }

    public static UUID codigoOperacion(String cliente, int indice) {
        return UUID.nameUUIDFromBytes(("lambda-shield:dataset-v1:" + cliente + ":" + indice).getBytes(StandardCharsets.UTF_8));
    }

    private Cliente cliente(DatasetDemo.Persona persona) {
        Cliente existente = clientes.findByCodigoCliente(persona.codigo()).orElse(null);
        String documento = "99000" + persona.numero();
        if (existente != null) {
            if (!documento.equals(existente.getNumeroDocumento())) throw colision(persona.codigo());
            return existente;
        }
        return clientes.save(Cliente.builder().codigoCliente(persona.codigo()).tipoDocumento("DNI")
                .numeroDocumento(documento).nombres(persona.nombres()).apellidoPaterno(persona.paterno())
                .apellidoMaterno(persona.materno()).fechaNacimiento(LocalDate.of(1990, 1, 1))
                .correoElectronico(persona.correo()).numeroTelefono("999000" + persona.numero())
                .promedioGasto(persona.promedio()).esActivo(true).build());
    }

    private void cuenta(DatasetDemo.Persona persona, Cliente cliente) {
        var existente = cuentas.findTopByCliente_IdClienteOrderByIdCuentaAsc(cliente.getIdCliente());
        if (existente.isPresent()) {
            if (!persona.cuenta().equals(existente.get().getNumeroCuenta())) throw colision(persona.cuenta());
            return;
        }
        cuentas.save(Cuenta.builder().cliente(cliente).numeroCuenta(persona.cuenta()).tipoCuenta(TipoCuenta.AHORROS)
                .codigoMoneda("PEN").saldoDisponible(new BigDecimal("0.00")).estadoCuenta(EstadoCuenta.ACTIVA)
                .fechaApertura(LocalDate.now().minusDays(366)).fechaCreacion(OffsetDateTime.now().minusDays(366)).build());
    }

    private void usuario(String nombre, String clave, String nombres, String rol, Long idCliente) {
        UsuarioSistema existente = usuarios.findByNombreUsuario(nombre).orElse(null);
        if (existente != null) {
            if (!java.util.Objects.equals(idCliente, existente.getIdCliente())
                    || !(nombre + "@example.test").equals(existente.getCorreo())) throw colision(nombre);
            // No restablece contraseñas, estado ni roles que el administrador haya cambiado después de la carga.
            return;
        }
        UsuarioSistema usuario = usuarios.save(UsuarioSistema.builder().nombreUsuario(nombre).nombres(nombres)
                .correo(nombre + "@example.test").hashPassword(encoder.encode(clave)).estadoUsuario(EstadoUsuario.ACTIVO)
                .idCliente(idCliente).fechaCreacion(OffsetDateTime.now()).build());
        asignaciones.save(UsuarioRol.builder().usuario(usuario).rol(roles.findByCodigoRol(rol).orElseThrow())
                .fechaAsignacion(OffsetDateTime.now()).build());
    }

    private void conocidos(Cliente cliente, OffsetDateTime ahora) {
        for (String normalizado : List.of("Chrome|Windows", "Edge|Windows", "Firefox|Windows", "Chrome|Android", "Safari|iOS", "Safari|macOS")) {
            String huella = DeviceFingerprint.from(cliente.getCodigoCliente(), normalizado);
            if (dispositivos.findByHuellaDispositivo(huella).isPresent()) continue;
            String[] partes = normalizado.split("\\|", 2);
            dispositivos.save(Dispositivo.builder().cliente(cliente).huellaDispositivo(huella)
                    .tipoDispositivo("Android".equals(partes[1]) || "iOS".equals(partes[1]) ? TipoDispositivo.MOVIL : TipoDispositivo.PC)
                    .navegador(partes[0]).sistemaOperativo(partes[1]).esConocido(true).direccionIp("127.0.0.1")
                    .fechaPrimerUso(ahora.minusDays(40)).fechaUltimoUso(ahora).build());
        }
    }

    private void crearTipo(String codigo, String nombre, NaturalezaTransaccion naturaleza) {
        TipoTransaccion existente = tipos.findByCodigoTipo(codigo).orElse(null);
        if (existente != null) {
            if (existente.getNaturaleza() != naturaleza) throw colision(codigo);
            return;
        }
        tipos.save(TipoTransaccion.builder().codigoTipo(codigo).nombreTipo(nombre).naturaleza(naturaleza).esActivo(true).build());
    }

    private static IllegalStateException colision(String dato) {
        return new IllegalStateException("Ya existe un registro incompatible con el conjunto demo: " + dato);
    }
    private static Movimiento habitual(int dias, int hora, String categoria, String monto) {
        return new Movimiento(dias, hora, categoria, new BigDecimal(monto), "local", "reconocido", 0, "recurrente", "conocida");
    }
    private static List<Movimiento> movimientos(DatasetDemo.Persona persona) {
        var resultado = new java.util.ArrayList<>(List.of(
                habitual(366, 10, "demo_apertura", persona.apertura().toPlainString()),
                habitual(32, 14, "servicios", "85.70"), habitual(29, 20, "restaurante", "48.90"),
                habitual(26, 16, "retail", "126.40"), habitual(366, 11, "demo_haberes", persona.haberes().toPlainString()),
                habitual(19, 15, "servicios", "110.25"), habitual(16, 11, "transferencia", "240.60"),
                habitual(13, 17, "retail", "315.80"), habitual(10, 13, "demo_reembolso", "120.35"),
                // Calibrados para la escala porcentual: MEDIO (≈49 %), ALTO (≈69 %) y CRITICO (100 %).
                new Movimiento(8 + (persona.numero() - 101) * 30, 18, "electronica", persona.promedio().multiply(new BigDecimal("2")),
                        "regional", "nuevo", 2, "recurrente", "conocida"),
                habitual(6, 20, "restaurante", "85.30"),
                new Movimiento(5, 2, "transferencia", persona.promedio().multiply(new BigDecimal("3")),
                        "regional", "nuevo", 3, "recurrente", "nueva"),
                new Movimiento(4, 1, "cripto", persona.promedio().multiply(new BigDecimal("7")),
                        "alto_riesgo", "nuevo", 6, "nuevo", "nueva"),
                habitual(3, 12, "servicios", "68.45"), habitual(2, 9, "demo_abono", "300.50"),
                habitual(1, 18, "retail", "92.15")
        ));
        // Se conserva el índice de las 16 operaciones originales para no duplicarlas.
        // Cada día tiene ingreso y egreso; las cuentas se alternan durante todo el año.
        for (int dias = 364; dias >= 0; dias--) {
            if (dias % DatasetDemo.PERSONAS.size() != persona.numero() - 101) continue;
            agregarMovimientoDiario(resultado, persona, dias);
        }
        // El rango anual del dashboard incluye también el día de hace 365 días.
        // Se añade al final para conservar los identificadores de la ampliación.
        if (365 % DatasetDemo.PERSONAS.size() == persona.numero() - 101) {
            agregarMovimientoDiario(resultado, persona, 365);
        }
        // Nueva tanda aditiva: seis cuentas por día, sin cambiar índices anteriores.
        for (int dias = 365; dias >= 0; dias--) {
            if (Math.floorMod(dias + persona.numero(), 4) == 0) continue;
            int variacion = Math.floorMod(dias * 53 + persona.numero() * 71, 130);
            resultado.add(habitual(dias, 9 + persona.numero() % 4, "demo_abono",
                    BigDecimal.valueOf(9000 + variacion * 95L, 2).toPlainString()));
            String categoria = List.of("servicios", "retail", "restaurante", "transferencia").get(variacion % 4);
            resultado.add(habitual(dias, 14 + persona.numero() % 5, categoria,
                    BigDecimal.valueOf(2000 + variacion * 95L, 2).toPlainString()));
        }
        return resultado;
    }
    private static void agregarMovimientoDiario(java.util.List<Movimiento> resultado, DatasetDemo.Persona persona, int dias) {
        int variacion = Math.floorMod(dias * 37 + persona.numero() * 11, 100);
        resultado.add(habitual(dias, 9, "demo_abono", BigDecimal.valueOf(12000 + variacion * 95L, 2).toPlainString()));
        String categoria = List.of("servicios", "retail", "restaurante", "transferencia").get(dias % 4);
        resultado.add(habitual(dias, 14, categoria, BigDecimal.valueOf(3500 + variacion * 80L, 2).toPlainString()));
    }

}
