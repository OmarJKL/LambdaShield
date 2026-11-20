package com.lambdashield.fraude.domain.entity;

import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notificacion_usuario", uniqueConstraints = @UniqueConstraint(
        name = "uq_notificacion_usuario_evaluacion_tipo", columnNames = {"id_usuario", "id_evaluacion", "tipo"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificacionUsuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario", nullable = false)
    private UsuarioSistema usuario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_evaluacion", nullable = false)
    private EvaluacionFraude evaluacion;
    @Enumerated(EnumType.STRING) @Column(name = "tipo", nullable = false, length = 20)
    private EstadoTransaccion tipo;
    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;
    @Column(name = "mensaje", nullable = false, length = 1000)
    private String mensaje;
    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;
    @Column(name = "fecha_lectura")
    private OffsetDateTime fechaLectura;
}
