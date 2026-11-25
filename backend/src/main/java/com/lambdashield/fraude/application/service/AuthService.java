package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.UsuarioSistema;
import com.lambdashield.fraude.application.validation.PoliticaClave;
import com.lambdashield.fraude.domain.enums.EstadoUsuario;
import com.lambdashield.fraude.repository.ClienteRepository;
import com.lambdashield.fraude.repository.UsuarioSistemaRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticación contra usuario_sistema / usuario_rol / rol.
 *
 * Las credenciales y los roles viven en PostgreSQL; no existe un catálogo de
 * usuarios en memoria.
 */
@Service
public class AuthService {

    public record UsuarioSesion(
            String usuario,
            String nombreCompleto,
            String rol,
            String codigoCliente,
            String credencial
    ) implements java.security.Principal, java.io.Serializable {
        @Override public String getName() { return usuario; }
        @Override public String toString() { return usuario; }
    }

    private final UsuarioSistemaRepository usuarioRepository;
    private final RolUsuarioService roles;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final String hashSimulado;

    public AuthService(UsuarioSistemaRepository usuarioRepository,
                       RolUsuarioService roles,
                       ClienteRepository clienteRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.roles = roles;
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.hashSimulado = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public Optional<UsuarioSesion> autenticar(String usuario, String clave) {
        if (usuario == null || usuario.isBlank() || usuario.length() > 80
                || !PoliticaClave.longitudAdmitida(clave) || clave.isBlank()) {
            return Optional.empty();
        }

        Optional<UsuarioSistema> encontrado = usuarioRepository.findByNombreUsuario(usuario.trim().toLowerCase(Locale.ROOT));
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(clave, hashSimulado);
            return Optional.empty();
        }

        UsuarioSistema usuarioSistema = encontrado.get();
        boolean claveValida = passwordEncoder.matches(clave, usuarioSistema.getHashPassword());
        if (usuarioSistema.getEstadoUsuario() != EstadoUsuario.ACTIVO || !claveValida) {
            return Optional.empty();
        }

        Optional<UsuarioSesion> sesion = sesionDe(usuarioSistema);
        if (sesion.isPresent()) {
            usuarioSistema.setUltimoAcceso(OffsetDateTime.now());
        }
        return sesion;
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioSesion> obtenerSesionActiva(String usuario) {
        return usuarioRepository.findByNombreUsuario(usuario)
                .filter(u -> u.getEstadoUsuario() == EstadoUsuario.ACTIVO).flatMap(this::sesionDe);
    }

    private Optional<UsuarioSesion> sesionDe(UsuarioSistema usuario) {
        String rol = roles.codigo(usuario.getIdUsuario());
        if ("SIN_ROL".equals(rol)) return Optional.empty();
        return Optional.of(new UsuarioSesion(usuario.getNombreUsuario(), usuario.getNombres(), rol,
                obtenerCodigoCliente(usuario.getIdCliente()), usuario.getHashPassword()));
    }

    private String obtenerCodigoCliente(Long idCliente) {
        if (idCliente == null) {
            return null;
        }
        return clienteRepository.findById(idCliente)
                .filter(com.lambdashield.fraude.domain.entity.Cliente::isEsActivo)
                .map(c -> c.getCodigoCliente())
                .orElse(null);
    }
}
