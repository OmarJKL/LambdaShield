package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.domain.entity.UsuarioRol;
import com.lambdashield.fraude.repository.UsuarioRolRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RolUsuarioService {
    private final UsuarioRolRepository repositorio;
    public RolUsuarioService(UsuarioRolRepository repositorio) { this.repositorio = repositorio; }
    public String codigo(Long idUsuario) {
        return resolver(repositorio.findByUsuario_IdUsuario(idUsuario));
    }
    public Map<Long, String> codigos(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return repositorio.findByUsuario_IdUsuarioIn(ids).stream().collect(Collectors.groupingBy(
                ur -> ur.getUsuario().getIdUsuario(), Collectors.collectingAndThen(Collectors.toList(), RolUsuarioService::resolver)));
    }
    private static String resolver(List<UsuarioRol> roles) {
        List<String> activos = roles.stream().map(UsuarioRol::getRol)
                .filter(rol -> rol != null && rol.isEsActivo()).map(rol -> rol.getCodigoRol()).toList();
        if (activos.contains("ADMIN")) return "ADMIN";
        if (activos.contains("ANALISTA")) return "ANALISTA";
        return activos.contains("CLIENTE") ? "CLIENTE" : "SIN_ROL";
    }
}
