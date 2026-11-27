package com.zaylope.registration.verification;

import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.common.validation.ValidIndianMobile;
import com.zaylope.registration.registration.IndianMobileNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobileNumberChangeRequest(
		@NotBlank(message = ApplicantInputValidator.MOBILE_MESSAGE)
		@Size(max = IndianMobileNumber.MAX_INPUT_LENGTH,
				message = ApplicantInputValidator.MOBILE_MESSAGE)
		@ValidIndianMobile
		String mobileNumber) {
}
