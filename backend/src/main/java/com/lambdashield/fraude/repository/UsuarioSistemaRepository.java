package com.lambdashield.fraude.repository;

import com.lambdashield.fraude.domain.entity.UsuarioSistema;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select distinct ur.usuario from UsuarioRol ur
            where ur.usuario.idCliente = :cliente and ur.rol.codigoRol = 'CLIENTE' and ur.rol.esActivo = true
            """)
    java.util.List<UsuarioSistema> destinatariosDeCliente(@org.springframework.data.repository.query.Param("cliente") Long cliente);
    Optional<UsuarioSistema> findByNombreUsuario(String nombreUsuario);
    boolean existsByCorreoIgnoreCase(String correo);
    boolean existsByCorreoIgnoreCaseAndIdUsuarioNot(String correo, Long id);

}
