package com.zaylope.registration.common.validation;

import com.zaylope.registration.registration.IndianMobileNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidIndianMobileValidator implements ConstraintValidator<ValidIndianMobile, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return IndianMobileNumber.isValid(value);
    }
}
