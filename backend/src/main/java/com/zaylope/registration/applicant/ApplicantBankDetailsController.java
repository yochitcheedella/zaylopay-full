package com.zaylope.registration.applicant;

import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.verification.MobileVerificationRequiredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me/bank-details")
public class ApplicantBankDetailsController {

	private final ApplicantBankDetailsRepository bankDetailsRepository;
	private final ApplicantRepository applicantRepository;
	private final BankEncryptionService encryptionService;

	public ApplicantBankDetailsController(
			ApplicantBankDetailsRepository bankDetailsRepository,
			ApplicantRepository applicantRepository,
			BankEncryptionService encryptionService) {
		this.bankDetailsRepository = bankDetailsRepository;
		this.applicantRepository = applicantRepository;
		this.encryptionService = encryptionService;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public ResponseEntity<BankDetailsResponse> getBankDetails(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);
		if (!applicant.isMobileVerified()) {
			throw new MobileVerificationRequiredException();
		}
		Optional<ApplicantBankDetails> details = bankDetailsRepository.findByApplicantId(principal.applicantId());
		return details.map(d -> ResponseEntity.ok(new BankDetailsResponse(
				d.getAccountHolderName(),
				d.getBankName(),
				d.getMaskedAccountNumber(),
				d.getIfscCode(),
				d.getConsentAt()
		))).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PutMapping
	@Transactional
	public ResponseEntity<BankDetailsResponse> saveBankDetails(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody BankDetailsSaveRequest request) {
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);
		if (!applicant.isMobileVerified()) {
			throw new MobileVerificationRequiredException();
		}

		Optional<ApplicantBankDetails> existingOpt = bankDetailsRepository.findByApplicantId(principal.applicantId());

		String rawAccount = request.accountNumber() == null ? "" : request.accountNumber().trim();

		// If new bank detail record, account number is required
		if (existingOpt.isEmpty() && rawAccount.isBlank()) {
			throw new IllegalArgumentException("Account number is required.");
		}

		String ciphertext = null;
		String last4 = null;
		String masked = null;
		if (!rawAccount.isBlank()) {
			if (!rawAccount.matches("^\\d{9,18}$")) {
				throw new IllegalArgumentException("Account number must contain between 9 and 18 digits.");
			}
			ciphertext = encryptionService.encrypt(rawAccount);
			last4 = BankEncryptionService.extractLast4(rawAccount);
			masked = BankEncryptionService.maskAccountNumber(rawAccount);
		}

		ApplicantBankDetails details;
		if (existingOpt.isPresent()) {
			details = existingOpt.get();
			details.updateDetails(
					request.accountHolderName().trim(),
					request.bankName().trim(),
					ciphertext,
					last4,
					masked,
					request.ifscCode().trim().toUpperCase());
		} else {
			details = new ApplicantBankDetails(
					principal.applicantId(),
					request.accountHolderName().trim(),
					request.bankName().trim(),
					ciphertext,
					last4,
					masked,
					request.ifscCode().trim().toUpperCase());
		}

		bankDetailsRepository.save(details);

		return ResponseEntity.ok(new BankDetailsResponse(
				details.getAccountHolderName(),
				details.getBankName(),
				details.getMaskedAccountNumber(),
				details.getIfscCode(),
				details.getConsentAt()));
	}

	public record BankDetailsSaveRequest(
			@NotBlank(message = "Account holder name is required")
			String accountHolderName,

			@NotBlank(message = "Bank name is required")
			String bankName,

			String accountNumber,

			@NotBlank(message = "IFSC code is required")
			String ifscCode) {
	}

	public record BankDetailsResponse(
			String accountHolderName,
			String bankName,
			String maskedAccountNumber,
			String ifscCode,
			Instant consentAt) {
	}
}
