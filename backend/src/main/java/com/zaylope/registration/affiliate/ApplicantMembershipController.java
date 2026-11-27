package com.zaylope.registration.affiliate;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.AffiliateAccessDeniedException;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.config.RazorpayProperties;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me/membership")
public class ApplicantMembershipController {

	private final AffiliateMembershipRepository membershipRepository;
	private final ApplicantRepository applicantRepository;
	private final AffiliateMembershipActivationService activationService;
	private final MembershipActivationPolicy activationPolicy;
	private final RazorpayProperties razorpayProperties;
	private final com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository;
	private final String clientBaseUrl;

	public ApplicantMembershipController(
			AffiliateMembershipRepository membershipRepository,
			ApplicantRepository applicantRepository,
			String clientBaseUrl) {
		this(
				membershipRepository,
				applicantRepository,
				null,
				new MembershipActivationPolicy(),
				new RazorpayProperties("", "", "", 59900L, "INR", false, "https://api.razorpay.com/v1"),
				null,
				clientBaseUrl);
	}

	public ApplicantMembershipController(
			AffiliateMembershipRepository membershipRepository,
			ApplicantRepository applicantRepository,
			AffiliateMembershipActivationService activationService,
			MembershipActivationPolicy activationPolicy,
			RazorpayProperties razorpayProperties,
			String clientBaseUrl) {
		this(
				membershipRepository,
				applicantRepository,
				activationService,
				activationPolicy,
				razorpayProperties,
				null,
				clientBaseUrl);
	}

	@Autowired
	public ApplicantMembershipController(
			AffiliateMembershipRepository membershipRepository,
			ApplicantRepository applicantRepository,
			AffiliateMembershipActivationService activationService,
			MembershipActivationPolicy activationPolicy,
			RazorpayProperties razorpayProperties,
			com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository,
			@Value("${app.cors.allowed-origin:http://localhost:5173}") String clientBaseUrl) {
		this.membershipRepository = membershipRepository;
		this.applicantRepository = applicantRepository;
		this.activationService = activationService;
		this.activationPolicy = activationPolicy;
		this.razorpayProperties = razorpayProperties;
		this.paymentOrderRepository = paymentOrderRepository;
		this.clientBaseUrl = clientBaseUrl;
	}

	@GetMapping
	public ResponseEntity<AffiliateMembershipDto> getMembership(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);

		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(principal.applicantId());
		if (activationPolicy.isAccountRestricted(applicant, membershipOpt)) {
			throw new AffiliateAccessDeniedException(
					"Your affiliate membership is currently restricted. Contact support@zaylope.com.");
		}

		if (!applicant.isPaymentPaid()) {
			throw new AffiliateAccessDeniedException(
					"Lifetime membership requires a verified ₹599 membership payment.");
		}
		if (!applicant.isContactVerified() || !applicant.isRegistrationSubmitted()) {
			throw new AffiliateAccessDeniedException(
					"Complete contact verification and application submission before accessing membership.");
		}

		Optional<PaymentTransaction> capturedTx = activationService != null
				? activationService.findVerifiedCapturedTransaction(principal.applicantId())
				: Optional.empty();

		AffiliateMembership membership = membershipOpt
				.filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
				.orElseThrow(() -> new AffiliateAccessDeniedException(
						"Membership activation is still pending completion. Please retry activation."));

		return ResponseEntity.ok(toMembershipDto(applicant, membership, capturedTx));
	}

	@PostMapping("/activate")
	public ResponseEntity<AffiliateMembershipDto> recoverActivation(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		if (activationService == null) {
			throw new AffiliateAccessDeniedException("Membership activation service is unavailable.");
		}
		var result = activationService.activateAfterPayment(principal.applicantId());
		if (result.restricted()) {
			throw new AffiliateAccessDeniedException(
					"Your account is restricted from activating membership.");
		}
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);
		Optional<PaymentTransaction> capturedTx =
				activationService.findVerifiedCapturedTransaction(principal.applicantId());
		return ResponseEntity.ok(toMembershipDto(applicant, result.membership(), capturedTx));
	}

	@GetMapping("/status")
	public ResponseEntity<OnboardingStatusDto> getOnboardingStatus(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);

		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(principal.applicantId());
		boolean restricted = activationPolicy.isAccountRestricted(applicant, membershipOpt);
		boolean eligible = activationPolicy.canAccessMembershipBenefits(applicant, membershipOpt);

		String affiliateId = eligible ? membershipOpt.get().getAffiliateId() : null;
		String referralCode = eligible ? membershipOpt.get().getReferralCode() : null;
		String referralUrl = referralCode != null ? clientBaseUrl + "/register?ref=" + referralCode : null;

		MembershipLifecycle.MembershipStatus membershipStatus = activationService != null
				? activationService.deriveMembershipStatus(applicant, membershipOpt)
				: (eligible
						? MembershipLifecycle.MembershipStatus.ACTIVE
						: applicant.isPaymentPaid()
								? MembershipLifecycle.MembershipStatus.PENDING_ACTIVATION
								: MembershipLifecycle.MembershipStatus.NOT_STARTED);

		MembershipLifecycle.AffiliateAccountStatus affiliateStatus = activationService != null
				? activationService.deriveAffiliateAccountStatus(applicant, membershipOpt)
				: (eligible
						? MembershipLifecycle.AffiliateAccountStatus.ACTIVE
						: MembershipLifecycle.AffiliateAccountStatus.PENDING);

		Optional<PaymentTransaction> capturedTx = activationService != null
				? activationService.findVerifiedCapturedTransaction(principal.applicantId())
				: Optional.empty();

		boolean activationPending = !restricted
				&& applicant.isPaymentPaid()
				&& membershipStatus == MembershipLifecycle.MembershipStatus.PENDING_ACTIVATION;

		Instant activatedAt = eligible
				? membershipOpt.get().getActivatedAt()
				: applicant.getApprovedAt();

		boolean hasPendingOrder = !restricted
				&& !eligible
				&& !applicant.isPaymentPaid()
				&& applicant.getApplicantId() != null
				&& paymentOrderRepository != null
				&& paymentOrderRepository
						.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicant.getApplicantId(), "CREATED")
						.isPresent();
		String nextDestination = activationPolicy.resolveDestination(applicant, membershipOpt, hasPendingOrder);

		return ResponseEntity.ok(new OnboardingStatusDto(
				applicant.getApplicantId(),
				applicant.getRegistrationId(),
				applicant.getRegistrationStatus(),
				applicant.getPaymentStatus(),
				applicant.getApprovalStatus(),
				affiliateId,
				referralCode,
				referralUrl,
				applicant.getRejectionReason(),
				activatedAt,
				applicant.getRegistrationSubmittedAt(),
				applicant.getFullName(),
				membershipStatus.name(),
				affiliateStatus.name(),
				"LIFETIME",
				capturedTx.map(PaymentTransaction::getAmountPaise).orElse(razorpayProperties.amountPaise()),
				capturedTx.map(PaymentTransaction::getCurrency).orElse(razorpayProperties.currency()),
				capturedTx.map(PaymentTransaction::getGatewayPaymentId).orElse(null),
				capturedTx.map(PaymentTransaction::getCapturedAt).orElse(null),
				activationPending,
				nextDestination));
	}

	private AffiliateMembershipDto toMembershipDto(
			Applicant applicant,
			AffiliateMembership membership,
			Optional<PaymentTransaction> capturedTx) {
		MembershipLifecycle.AffiliateAccountStatus accountStatus = activationService != null
				? activationService.deriveAffiliateAccountStatus(applicant, Optional.of(membership))
				: MembershipLifecycle.AffiliateAccountStatus.ACTIVE;
		return new AffiliateMembershipDto(
				membership.getAffiliateId(),
				membership.getReferralCode(),
				clientBaseUrl + "/register?ref=" + membership.getReferralCode(),
				membership.getStatus(),
				membership.getTier(),
				membership.getActivatedAt(),
				applicant.getFullName(),
				"LIFETIME",
				"ACTIVE",
				accountStatus.name(),
				capturedTx.map(PaymentTransaction::getAmountPaise).orElse(razorpayProperties.amountPaise()),
				capturedTx.map(PaymentTransaction::getCurrency).orElse(razorpayProperties.currency()),
				capturedTx.isPresent() || applicant.isPaymentPaid() ? "CAPTURED" : applicant.getPaymentStatus(),
				capturedTx.map(PaymentTransaction::getGatewayOrderId).orElse(null),
				capturedTx.map(PaymentTransaction::getGatewayPaymentId).orElse(null),
				capturedTx.map(PaymentTransaction::getCapturedAt).orElse(null),
				false,
				"DASHBOARD");
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record AffiliateMembershipDto(
			String affiliateId,
			String referralCode,
			String referralUrl,
			String status,
			String tier,
			Instant activatedAt,
			String applicantName,
			String membershipType,
			String membershipStatus,
			String affiliateStatus,
			Long amountPaise,
			String currency,
			String paymentStatus,
			String orderId,
			String paymentId,
			Instant paidAt,
			boolean activationPending,
			String nextDestination) {
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record OnboardingStatusDto(
			Long applicantId,
			String registrationId,
			String registrationStatus,
			String paymentStatus,
			String approvalStatus,
			String affiliateId,
			String referralCode,
			String referralUrl,
			String rejectionReason,
			Instant approvedAt,
			Instant submittedAt,
			String applicantName,
			String membershipStatus,
			String affiliateStatus,
			String membershipType,
			Long amountPaise,
			String currency,
			String paymentId,
			Instant paidAt,
			boolean activationPending,
			String nextDestination) {
	}
}
