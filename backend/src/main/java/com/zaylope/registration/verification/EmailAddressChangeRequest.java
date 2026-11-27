package com.zaylope.registration.verification;

import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.common.validation.ValidEmailAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailAddressChangeRequest(
		@NotBlank(message = ApplicantInputValidator.EMAIL_MESSAGE)
		@Size(max = ApplicantInputValidator.MAX_EMAIL_LENGTH,
				message = ApplicantInputValidator.EMAIL_MESSAGE)
		@ValidEmailAddress
		String email) {}
