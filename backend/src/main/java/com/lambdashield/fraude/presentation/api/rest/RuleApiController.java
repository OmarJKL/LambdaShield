package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.service.ReglaConsultaService;
import com.lambdashield.fraude.presentation.dto.response.ReglaResponse;
import com.lambdashield.fraude.presentation.mapper.ReglaResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller para /api/v1/rule: expone las reglas activas del motor antifraude,
 * leídas desde la tabla regla_fraude.
 */
@RestController
@RequestMapping("/api/v1/rule")
public class RuleApiController {

    private final ReglaConsultaService reglaService;
    private final com.lambdashield.fraude.application.service.EstadoReglaService estadoService;
    public record EstadoRequest(@jakarta.validation.constraints.NotNull Boolean activa) {}
    private final ReglaResponseMapper mapper;

    public RuleApiController(ReglaConsultaService reglaService, ReglaResponseMapper mapper, com.lambdashield.fraude.application.service.EstadoReglaService estadoService) {
        this.estadoService = estadoService;
        this.reglaService = reglaService;
        this.mapper = mapper;
    }

    @PatchMapping("/{codigo}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable String codigo,
            @jakarta.validation.Valid @RequestBody EstadoRequest request) {
        estadoService.establecer(codigo, request.activa());
        return ResponseEntity.noContent().build();
    }

    /** Devuelve la lista de reglas activas almacenadas en la base de datos. */
    @GetMapping
    public ResponseEntity<List<ReglaResponse>> list() {
        return ResponseEntity.ok(reglaService.listar().stream().map(mapper::toResponse).toList());
    }
}
