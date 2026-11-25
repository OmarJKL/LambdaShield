package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.Auditoria;
import com.lambdashield.fraude.repository.AuditoriaRepository;
import com.lambdashield.fraude.repository.UsuarioSistemaRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/** Registro de acciones administrativas sobre el catálogo de reglas. */
@Service
public class AuditoriaService {
    private final AuditoriaRepository auditoria;
    private final UsuarioSistemaRepository usuarios;

    public AuditoriaService(AuditoriaRepository auditoria, UsuarioSistemaRepository usuarios) {
        this.auditoria = auditoria;
        this.usuarios = usuarios;
    }

    public void registrar(String usuario, String entidad, String idEntidad, String accion,
                          String datosAnteriores, String datosNuevos, String direccionIp) {
        auditoria.save(Auditoria.builder()
                .usuario(usuario == null ? null : usuarios.findByNombreUsuario(usuario).orElse(null))
                .entidad(entidad).idEntidad(idEntidad).accion(accion)
                .datosAnteriores(datosAnteriores).datosNuevos(datosNuevos)
                .direccionIp(direccionIp).fechaEvento(OffsetDateTime.now()).build());
    }
}
