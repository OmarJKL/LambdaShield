package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.UsuarioRol;
import com.lambdashield.fraude.domain.entity.UsuarioRolId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {
    @EntityGraph(attributePaths = {"rol"})
    List<UsuarioRol> findByUsuario_IdUsuario(Long idUsuario);

    @EntityGraph(attributePaths = {"rol"})
    List<UsuarioRol> findByUsuario_IdUsuarioIn(Collection<Long> ids);

    @Query("""
            select count(distinct ur.usuario.idUsuario) from UsuarioRol ur
            where ur.usuario.idUsuario <> :excluido
              and ur.usuario.estadoUsuario = com.lambdashield.fraude.domain.enums.EstadoUsuario.ACTIVO
              and ur.rol.codigoRol = 'ADMIN' and ur.rol.esActivo = true
            """)
    long contarOtrosAdministradoresActivos(@Param("excluido") Long excluido);
}
