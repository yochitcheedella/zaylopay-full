package com.zaylope.registration.registration;

import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.common.validation.ValidEmailAddress;
import com.zaylope.registration.common.validation.ValidFullName;
import com.zaylope.registration.common.validation.ValidIndianMobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicantRegistrationRequest(
		@NotBlank(message = ApplicantInputValidator.FULL_NAME_MESSAGE)
		@Size(max = ApplicantInputValidator.MAX_FULL_NAME_LENGTH,
				message = ApplicantInputValidator.FULL_NAME_MESSAGE)
		@ValidFullName
		String fullName,

		@NotBlank(message = ApplicantInputValidator.MOBILE_MESSAGE)
		@Size(max = IndianMobileNumber.MAX_INPUT_LENGTH,
				message = ApplicantInputValidator.MOBILE_MESSAGE)
		@ValidIndianMobile
		String mobileNumber,

		@NotBlank(message = ApplicantInputValidator.EMAIL_MESSAGE)
		@Size(max = ApplicantInputValidator.MAX_EMAIL_LENGTH,
				message = ApplicantInputValidator.EMAIL_MESSAGE)
		@ValidEmailAddress
		String email) {
}
