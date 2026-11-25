package com.lambdashield.fraude.presentation.dto.request;

import com.lambdashield.fraude.application.model.DefinicionRegla;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Prueba sin registrar: escenarios con el mismo contrato que una evaluación, reglas
 * candidatas del catálogo (pueden estar inactivas) y, opcionalmente, un borrador sin guardar.
 */
public record SimulacionReglasRequest(
    @NotEmpty(message = "Envía al menos un escenario")
    @Size(max = 100, message = "Se admiten hasta 100 escenarios por prueba")
    List<@Valid TransaccionCrearRequest> escenarios,

    @Size(max = 20, message = "Se admiten hasta 20 reglas candidatas")
    List<@Pattern(regexp = "[A-Za-z0-9_-]{1,30}", message = "Código de regla inválido") String> candidatas,

    DefinicionRegla borrador
) {}
