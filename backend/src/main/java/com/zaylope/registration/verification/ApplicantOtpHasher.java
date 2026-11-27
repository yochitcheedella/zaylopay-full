package com.zaylope.registration.verification;

import com.zaylope.registration.config.VerificationProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ApplicantOtpHasher {

	private final VerificationProperties properties;

	public ApplicantOtpHasher(VerificationProperties properties) {
		this.properties = properties;
	}


	public String hash(String issuanceId, long applicantId, ApplicantVerificationChannel channel, String destination, String code) {
		return fingerprint("APPLICANT_CONTACT\u0000" + issuanceId + "\u0000" + applicantId + "\u0000" + channel + "\u0000" + destination + "\u0000" + code);
	}

	public String fingerprint(String context) {
		if (!properties.hasOtpHashKey()) {
			throw new VerificationDeliveryUnavailableException();
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
					properties.otpHashKey().getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"));
			byte[] digest = mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HMAC-SHA256 is not available.", exception);
		}
	}

	public boolean matches(String storedHash, String issuanceId, long applicantId, ApplicantVerificationChannel channel, String destination, String code) {
		return storedHash != null && MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII),
				hash(issuanceId, applicantId, channel, destination, code).getBytes(StandardCharsets.US_ASCII));
	}
}
