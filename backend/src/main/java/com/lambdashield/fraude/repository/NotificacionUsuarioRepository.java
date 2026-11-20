package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.NotificacionUsuario;
import com.lambdashield.fraude.domain.enums.EstadoTransaccion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;

public interface NotificacionUsuarioRepository extends JpaRepository<NotificacionUsuario, Long> {
    @EntityGraph(attributePaths = {"evaluacion", "evaluacion.transaccion", "evaluacion.transaccion.caracteristica"})
    List<NotificacionUsuario> findByUsuario_IdUsuarioOrderByFechaCreacionDescIdNotificacionDesc(Long usuario, Pageable pagina);
    long countByUsuario_IdUsuarioAndFechaLecturaIsNull(Long usuario);
    boolean existsByUsuario_IdUsuarioAndEvaluacion_IdEvaluacionAndTipo(Long usuario, Long evaluacion, EstadoTransaccion tipo);
    boolean existsByIdNotificacionAndUsuario_IdUsuario(Long id, Long usuario);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificacionUsuario n set n.fechaLectura = :fecha where n.idNotificacion = :id and n.usuario.idUsuario = :usuario and n.fechaLectura is null")
    int marcarLeida(@Param("id") Long id, @Param("usuario") Long usuario, @Param("fecha") OffsetDateTime fecha);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificacionUsuario n set n.fechaLectura = :fecha where n.usuario.idUsuario = :usuario and n.fechaLectura is null")
    int marcarTodasLeidas(@Param("usuario") Long usuario, @Param("fecha") OffsetDateTime fecha);
}
