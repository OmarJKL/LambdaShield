package com.lambdashield.fraude.presentation.dto.request;

import com.lambdashield.fraude.presentation.validation.ClaveValida;

public record UsuarioClaveRequest(
    @ClaveValida
    String clave
) {}
