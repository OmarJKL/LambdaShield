package com.lambdashield.fraude.presentation.api.rest;

import com.lambdashield.fraude.application.command.EvaluarTransaccionCommand;
import com.lambdashield.fraude.application.model.DefinicionRegla;
import com.lambdashield.fraude.application.model.ResultadoSimulacion;
import com.lambdashield.fraude.application.service.EstadoReglaService;
import com.lambdashield.fraude.application.service.ReglaConsultaService;
import com.lambdashield.fraude.application.service.ReglaPersonalizadaService;
import com.lambdashield.fraude.application.service.SimulacionReglasService;
import com.lambdashield.fraude.presentation.dto.request.SimulacionReglasRequest;
import com.lambdashield.fraude.presentation.dto.response.ReglaResponse;
import com.lambdashield.fraude.presentation.mapper.ReglaResponseMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;

/**
 * REST Controller para /api/v1/rule: lista las reglas activas del motor antifraude
 * (tabla regla_fraude), permite al ADMIN crear reglas definidas, activarlas o
 * desactivarlas y probar escenarios sin registrar operaciones.
 */
@RestController
@RequestMapping("/api/v1/rule")
public class RuleApiController {

    public record EstadoRequest(@NotNull Boolean activa) {}
    public record SimulacionResponse(List<ResultadoSimulacion> resultados) {}

    private final ReglaConsultaService reglaService;
    private final EstadoReglaService estadoService;
    private final ReglaPersonalizadaService personalizadas;
    private final SimulacionReglasService simulacion;
    private final ReglaResponseMapper mapper;

    public RuleApiController(ReglaConsultaService reglaService, ReglaResponseMapper mapper, EstadoReglaService estadoService,
                             ReglaPersonalizadaService personalizadas, SimulacionReglasService simulacion) {
        this.estadoService = estadoService;
        this.reglaService = reglaService;
        this.personalizadas = personalizadas;
        this.simulacion = simulacion;
        this.mapper = mapper;
    }

    /** Devuelve la lista de reglas activas almacenadas en la base de datos. */
    @GetMapping
    public ResponseEntity<List<ReglaResponse>> list() {
        return ResponseEntity.ok(reglaService.listar().stream().map(mapper::toResponse).toList());
    }

    /** Guarda la definición del formulario como regla RC inactiva. */
    @PostMapping
    public ResponseEntity<ReglaResponse> crear(@RequestBody DefinicionRegla definicion,
            @SessionAttribute(value = "usuario", required = false) String usuario, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(personalizadas.crear(definicion, usuario, httpRequest.getRemoteAddr())));
    }

    @PatchMapping("/{codigo}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable String codigo, @Valid @RequestBody EstadoRequest request,
            @SessionAttribute(value = "usuario", required = false) String usuario, HttpServletRequest httpRequest) {
        estadoService.establecer(codigo, request.activa(), usuario, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** Evalúa escenarios con el catálogo vigente y con las candidatas, sin persistir nada. */
    @PostMapping("/simulation")
    public SimulacionResponse simular(@Valid @RequestBody SimulacionReglasRequest request) {
        var escenarios = request.escenarios().stream().map(e -> new EvaluarTransaccionCommand(
                e.cliente(), e.monto(), e.categoria(), e.hora(), e.pais(), e.dispositivo(), e.operacionesUltimaHora(),
                e.beneficiario(), e.ip(), null)).toList();
        return new SimulacionResponse(simulacion.simular(escenarios,
                request.candidatas() == null ? null : new HashSet<>(request.candidatas()), request.borrador()));
    }
}
