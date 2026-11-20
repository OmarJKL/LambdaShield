package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.command.UsuarioActualizarCommand;
import com.lambdashield.fraude.application.command.UsuarioCrearCommand;
import com.lambdashield.fraude.application.model.UsuarioResumen;
import com.lambdashield.fraude.domain.entity.Cliente;
import com.lambdashield.fraude.domain.entity.Rol;
import com.lambdashield.fraude.domain.entity.UsuarioRol;
import com.lambdashield.fraude.domain.entity.UsuarioSistema;
import com.lambdashield.fraude.domain.enums.EstadoUsuario;
import com.lambdashield.fraude.repository.ClienteRepository;
import com.lambdashield.fraude.repository.RolRepository;
import com.lambdashield.fraude.repository.UsuarioRolRepository;
import com.lambdashield.fraude.repository.UsuarioSistemaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios del sistema: alta, consulta, actualización, cambio de
 * clave y baja física. Reglas: usuario y correo únicos, siempre al menos un
 * ADMIN activo y nadie puede darse de baja a sí mismo.
 */
@Service
public class UsuarioAdminService {

    private static final String ADMIN = "ADMIN";

    private final UsuarioSistemaRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolUsuarioService roles;

    public UsuarioAdminService(UsuarioSistemaRepository usuarioRepository,
                               UsuarioRolRepository usuarioRolRepository,
                               RolRepository rolRepository,
                               ClienteRepository clienteRepository,
                               PasswordEncoder passwordEncoder, RolUsuarioService roles) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.rolRepository = rolRepository;
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumen> listar() {
        List<UsuarioSistema> usuarios = usuarioRepository.findAll(Sort.by(
                Direction.DESC, "idUsuario"));
        var codigos = roles.codigos(usuarios.stream().map(UsuarioSistema::getIdUsuario).toList());
        var clientes = clienteRepository.findAllById(usuarios.stream().map(UsuarioSistema::getIdCliente)
                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Cliente::getIdCliente, Cliente::getCodigoCliente));
        return usuarios.stream().map(u -> toResumen(u, codigos.getOrDefault(u.getIdUsuario(), "SIN_ROL"),
                clientes.get(u.getIdCliente()))).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResumen buscar(Long idUsuario) {
        return toResumen(obtener(idUsuario));
    }

    @Transactional
    public UsuarioResumen crear(UsuarioCrearCommand command) {
        String usuario = normalizar(command.usuario());
        validarUnicidad(usuario, command.correo(), null);

        UsuarioSistema nuevo = new UsuarioSistema();
        nuevo.setNombreUsuario(usuario);
        nuevo.setNombres(command.nombres().trim());
        nuevo.setCorreo(command.correo().trim().toLowerCase(Locale.ROOT));
        nuevo.setHashPassword(passwordEncoder.encode(command.clave()));
        nuevo.setEstadoUsuario(EstadoUsuario.ACTIVO);
        nuevo.setFechaCreacion(OffsetDateTime.now());
        nuevo.setIdCliente(resolverCliente(command.rol(), command.codigoCliente()));
        usuarioRepository.save(nuevo);

        asignarRol(nuevo, command.rol());
        return toResumen(nuevo);
    }

    @Transactional
    public UsuarioResumen actualizar(Long idUsuario, UsuarioActualizarCommand command) {
        UsuarioSistema usuario = obtener(idUsuario);
        validarUnicidad(null, command.correo(), idUsuario);

        String rolAnterior = roles.codigo(usuario.getIdUsuario());
        usuario.setNombres(command.nombres().trim());
        usuario.setCorreo(command.correo().trim().toLowerCase(Locale.ROOT));
        usuario.setEstadoUsuario(EstadoUsuario.valueOf(command.estado().toUpperCase(Locale.ROOT)));

        String rolNuevo = command.rol().toUpperCase(Locale.ROOT);
        boolean dejaDeSerAdmin = ADMIN.equals(rolAnterior)
                && (!rolNuevo.equals(rolAnterior) || usuario.getEstadoUsuario() != EstadoUsuario.ACTIVO);
        if (dejaDeSerAdmin && contarOtrosAdministradoresActivos(idUsuario) == 0) {
            throw new IllegalArgumentException("No se puede quitar el rol ADMIN o bloquear al último administrador activo");
        }

        if (!rolNuevo.equals(rolAnterior)) {
            usuarioRolRepository.deleteAll(usuarioRolRepository.findByUsuario_IdUsuario(idUsuario));
            asignarRol(usuario, rolNuevo);
        }

        usuario.setIdCliente(resolverCliente(rolNuevo, command.codigoCliente()));
        usuarioRepository.save(usuario);
        return toResumen(usuario);
    }

    @Transactional
    public void cambiarClave(Long idUsuario, String clave) {
        UsuarioSistema usuario = obtener(idUsuario);
        usuario.setHashPassword(passwordEncoder.encode(clave));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void eliminar(Long idUsuario, String usuarioActual) {
        UsuarioSistema usuario = obtener(idUsuario);
        if (usuario.getNombreUsuario().equals(usuarioActual)) {
            throw new IllegalArgumentException("No puedes eliminar tu propio usuario");
        }
        if (ADMIN.equals(roles.codigo(idUsuario)) && usuario.getEstadoUsuario() == EstadoUsuario.ACTIVO
                && contarOtrosAdministradoresActivos(idUsuario) == 0) {
            throw new IllegalArgumentException("No se puede eliminar al último administrador activo");
        }

        usuarioRolRepository.deleteAll(usuarioRolRepository.findByUsuario_IdUsuario(idUsuario));
        usuarioRepository.delete(usuario);
    }

    private UsuarioSistema obtener(Long idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new UsuarioNoEncontradoException(idUsuario));
    }

    private void validarUnicidad(String usuario, String correo, Long idExcluido) {
        if (usuario != null) {
            usuarioRepository.findByNombreUsuario(usuario)
                    .filter(u -> idExcluido == null || !u.getIdUsuario().equals(idExcluido))
                    .ifPresent(u -> {
                        throw new IllegalArgumentException("El nombre de usuario ya existe: " + usuario);
                    });
        }
        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        boolean existe = idExcluido == null ? usuarioRepository.existsByCorreoIgnoreCase(correoNormalizado)
                : usuarioRepository.existsByCorreoIgnoreCaseAndIdUsuarioNot(correoNormalizado, idExcluido);
        if (existe) throw new IllegalArgumentException("El correo ya está registrado: " + correo);

    }

    private void asignarRol(UsuarioSistema usuario, String codigoRol) {
        Rol rol = rolRepository.findByCodigoRol(codigoRol.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Rol desconocido: " + codigoRol));
        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuarioRol.setFechaAsignacion(OffsetDateTime.now());
        usuarioRolRepository.save(usuarioRol);
    }

    private Long resolverCliente(String codigoRol, String codigoCliente) {
        String rol = codigoRol.toUpperCase(Locale.ROOT);
        if (codigoCliente == null || codigoCliente.isBlank()) {
            if ("CLIENTE".equals(rol)) {
                throw new IllegalArgumentException("Un usuario CLIENTE necesita un código de cliente");
            }
            return null;
        }
        Cliente cliente = clienteRepository.findByCodigoCliente(codigoCliente.trim())
                .orElseThrow(() -> new ClienteNoEncontradoException(codigoCliente.trim()));
        return cliente.getIdCliente();
    }

    private long contarOtrosAdministradoresActivos(Long idExcluido) {
        return usuarioRolRepository.contarOtrosAdministradoresActivos(idExcluido);
    }

    private UsuarioResumen toResumen(UsuarioSistema usuario) {
        String codigoCliente = Optional.ofNullable(usuario.getIdCliente())
                .flatMap(clienteRepository::findById)
                .map(Cliente::getCodigoCliente)
                .orElse(null);
        return toResumen(usuario, roles.codigo(usuario.getIdUsuario()), codigoCliente);
    }

    private UsuarioResumen toResumen(UsuarioSistema usuario, String rol, String codigoCliente) {
        return new UsuarioResumen(
                usuario.getIdUsuario(),
                usuario.getNombreUsuario(),
                usuario.getNombres(),
                usuario.getCorreo(),
                usuario.getEstadoUsuario().name(),
                rol,
                codigoCliente,
                usuario.getUltimoAcceso() != null ? usuario.getUltimoAcceso().toLocalDateTime() : null
        );
    }

    private static String normalizar(String usuario) {
        return usuario.trim().toLowerCase(Locale.ROOT);
    }
}
