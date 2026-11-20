package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.RevisionRegla;
import com.lambdashield.fraude.application.service.AsistenteReglasService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rule/assistant")
public class RuleAssistantApiController {
    public record Estado(boolean disponible, String detalle) {}
    public record RevisionResponse(DefinicionRegla definicion, RevisionRegla revision) {}
    private final AsistenteReglasService asistente;
    public RuleAssistantApiController(AsistenteReglasService asistente) { this.asistente = asistente; }
    @GetMapping("/status")
    public Estado estado() { return new Estado(asistente.disponible(), asistente.configuracion()); }
    @PostMapping("/review")
    public RevisionResponse revisar(@RequestBody DefinicionRegla definicion) {
        return new RevisionResponse(definicion, asistente.revisar(definicion));
    }
}
