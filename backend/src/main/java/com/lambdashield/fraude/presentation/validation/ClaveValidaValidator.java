package com.lambdashield.fraude.presentation.validation;

import com.lambdashield.fraude.application.validation.PoliticaClave;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ClaveValidaValidator implements ConstraintValidator<ClaveValida, String> {
    @Override
    public boolean isValid(String clave, ConstraintValidatorContext contexto) {
        return PoliticaClave.esValida(clave);
    }
}
