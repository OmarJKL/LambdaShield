package com.lambdashield.fraude.presentation.validation;

import com.lambdashield.fraude.application.validation.PoliticaClave;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ClaveValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClaveValida {
    String message() default PoliticaClave.MENSAJE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
