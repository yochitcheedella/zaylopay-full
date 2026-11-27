package com.zaylope.registration.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidEmailAddressValidator implements ConstraintValidator<ValidEmailAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return ApplicantInputValidator.isValidEmail(value);
    }
}
