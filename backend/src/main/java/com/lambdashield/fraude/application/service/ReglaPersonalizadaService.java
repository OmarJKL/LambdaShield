package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.ReglaCatalogo;
import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.domain.enums.NivelRiesgo;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica en el catálogo una regla definida en el formulario. Se guarda la definición del
 * administrador (nunca la sugerencia del asistente) y se crea INACTIVA: activarla es un paso aparte.
 */
@Service
public class ReglaPersonalizadaService {
    private static final Pattern CODIGO = Pattern.compile("RC(\\d{3,})");

    private final ReglaFraudeRepository repositorio;
    private final ReglaDefinidaEvaluador definidas;
    private final AuditoriaService auditoria;

    public ReglaPersonalizadaService(ReglaFraudeRepository repositorio, ReglaDefinidaEvaluador definidas,
                                     AuditoriaService auditoria) {
        this.repositorio = repositorio;
        this.definidas = definidas;
        this.auditoria = auditoria;
    }

    @Transactional
    public ReglaCatalogo crear(DefinicionRegla definicion, String usuario, String direccionIp) {
        definicion.validar();
        // El bloqueo del catálogo evita que dos altas simultáneas obtengan el mismo código.
        int siguiente = repositorio.bloquearCatalogo().stream()
                .map(r -> CODIGO.matcher(r.getCodigoRegla()))
                .filter(java.util.regex.Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max().orElse(0) + 1;
        String codigo = String.format("RC%03d", siguiente);
        String json = definidas.serializar(definicion);
        ReglaFraude regla = repositorio.save(ReglaFraude.builder()
                .codigoRegla(codigo)
                .nombreRegla(definicion.nombre().trim())
                .descripcion(definidas.resumen(definicion))
                .nivelRiesgo(NivelRiesgo.MEDIO)
                .peso(BigDecimal.valueOf(definicion.puntos()))
                .parametrosJson(json)
                .versionRegla(1)
                .esActiva(false)
                .fechaInicioVigencia(OffsetDateTime.now())
                .build());
        auditoria.registrar(usuario, "regla_fraude", codigo, "CREAR", null, json, direccionIp);
        return new ReglaCatalogo(regla.getCodigoRegla(), regla.getNombreRegla(), regla.getDescripcion(),
                definicion.puntos(), false, null, true);
    }
}
