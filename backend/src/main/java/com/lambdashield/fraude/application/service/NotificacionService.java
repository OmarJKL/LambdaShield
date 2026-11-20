package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.NotificacionResumen;
import com.lambdashield.fraude.domain.entity.*;
import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import com.lambdashield.fraude.domain.fraud.FactorEvaluado;
import com.lambdashield.fraude.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificacionService {
    public record Bandeja(List<NotificacionResumen> items, long noLeidas) {}
    private final NotificacionUsuarioRepository notificaciones;
    private final UsuarioSistemaRepository usuarios;
    public NotificacionService(NotificacionUsuarioRepository notificaciones, UsuarioSistemaRepository usuarios) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
    }
    @Transactional
    public void notificar(EvaluacionFraude evaluacion, List<FactorEvaluado> factores) {
        EstadoTransaccion tipo = evaluacion.getTransaccion().getEstadoTransaccion();
        if (tipo != EstadoTransaccion.BLOQUEADA && tipo != EstadoTransaccion.RETENIDA) return;
        String operacion = String.format("TR-%05d", evaluacion.getIdEvaluacion());
        String titulo = tipo == EstadoTransaccion.BLOQUEADA ? "Operación bloqueada" : "Operación retenida";
        String motivos = factores.stream().filter(f -> f.puntaje() > 0)
                .map(f -> f.etiqueta() + " (" + f.puntaje() + "/" + f.max() + " puntos)")
                .collect(Collectors.joining(", "));
        String mensaje = operacion + " · S/ " + evaluacion.getTransaccion().getMontoTransaccion().toPlainString()
                + ". Tu saldo no fue descontado. " + (motivos.isBlank() ? "Revisa el detalle de la operación." : "Factores: " + motivos + ".");
        if (mensaje.length() > 1000) mensaje = mensaje.substring(0, 1000);
        for (UsuarioSistema usuario : usuarios.destinatariosDeCliente(
                evaluacion.getTransaccion().getCuenta().getCliente().getIdCliente())) {
            if (!notificaciones.existsByUsuario_IdUsuarioAndEvaluacion_IdEvaluacionAndTipo(
                    usuario.getIdUsuario(), evaluacion.getIdEvaluacion(), tipo)) {
                notificaciones.save(NotificacionUsuario.builder().usuario(usuario).evaluacion(evaluacion).tipo(tipo)
                        .titulo(titulo).mensaje(mensaje).fechaCreacion(evaluacion.getFechaEvaluacion()).build());
            }
        }
    }
    @Transactional(readOnly = true)
    public Bandeja listar(String nombreUsuario, int pagina) {
        Long usuario = usuario(nombreUsuario);
        List<NotificacionResumen> items = notificaciones
                .findByUsuario_IdUsuarioOrderByFechaCreacionDescIdNotificacionDesc(usuario, PageRequest.of(pagina, 20))
                .stream().map(n -> new NotificacionResumen(n.getIdNotificacion(), n.getTipo().name(), n.getTitulo(), n.getMensaje(),
                        String.format("TR-%05d", n.getEvaluacion().getIdEvaluacion()),
                        n.getEvaluacion().getTransaccion().getMontoTransaccion(), n.getEvaluacion().getFraudeScore().intValue(),
                        n.getFechaCreacion(), n.getFechaLectura() != null)).toList();
        return new Bandeja(items, notificaciones.countByUsuario_IdUsuarioAndFechaLecturaIsNull(usuario));
    }
    @Transactional
    public void leer(String nombreUsuario, Long id) {
        Long usuario = usuario(nombreUsuario);
        if (!notificaciones.existsByIdNotificacionAndUsuario_IdUsuario(id, usuario)) throw new NotificacionNoEncontradaException();
        notificaciones.marcarLeida(id, usuario, OffsetDateTime.now());
    }
    @Transactional
    public void leerTodas(String nombreUsuario) {
        notificaciones.marcarTodasLeidas(usuario(nombreUsuario), OffsetDateTime.now());
    }
    private Long usuario(String nombre) {
        return usuarios.findByNombreUsuario(nombre).orElseThrow(NotificacionNoEncontradaException::new).getIdUsuario();
    }
}
