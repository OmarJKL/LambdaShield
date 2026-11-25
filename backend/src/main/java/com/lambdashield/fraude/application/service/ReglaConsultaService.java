package com.lambdashield.fraude.application.service;

import com.lambdashield.fraude.application.model.ReglaCatalogo;
import com.lambdashield.fraude.domain.entity.ReglaFraude;
import com.lambdashield.fraude.domain.fraud.FactorRegla;
import com.lambdashield.fraude.repository.ReglaFraudeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReglaConsultaService {

    private final ReglaFraudeRepository reglaFraudeRepository;

    public ReglaConsultaService(ReglaFraudeRepository reglaFraudeRepository) {
        this.reglaFraudeRepository = reglaFraudeRepository;
    }

    @Transactional(readOnly = true)
    public List<ReglaCatalogo> listar() {
        return listarTodas().stream().filter(ReglaCatalogo::activa).toList();
    }

    @Transactional(readOnly = true)
    public List<ReglaCatalogo> listarTodas() {
        List<ReglaFraude> reglas = reglaFraudeRepository.findAllByOrderByCodigoReglaAsc();
        Map<String, BigDecimal> participacion = CatalogoReglasService.participaciones(reglas);
        return reglas.stream().map(r -> toCatalogo(r, participacion.get(r.getCodigoRegla()))).toList();
    }

    private ReglaCatalogo toCatalogo(ReglaFraude regla, BigDecimal participacion) {
        // Un peso inválido no debe impedir ver el catálogo: se muestra para poder corregirlo.
        Integer peso;
        try { peso = CatalogoReglasService.pesoEntero(regla); } catch (IllegalStateException ex) { peso = null; }
        return new ReglaCatalogo(regla.getCodigoRegla(), regla.getNombreRegla(), regla.getDescripcion(), peso,
                regla.isEsActiva(), participacion, FactorRegla.porCodigo(regla.getCodigoRegla()).isEmpty());
    }
}
