package com.zaylope.registration.admin;

import com.zaylope.registration.admin.AdminDtos.AdminApprovalResponse;
import com.zaylope.registration.admin.AdminDtos.AdminMetricsResponse;
import com.zaylope.registration.admin.AdminDtos.AdminRejectionResponse;
import com.zaylope.registration.admin.AdminDtos.AffiliateSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantDetailResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApprovalHistoryInfo;
import com.zaylope.registration.admin.AdminDtos.BankInfo;
import com.zaylope.registration.admin.AdminDtos.DocumentInfo;
import com.zaylope.registration.admin.AdminDtos.DocumentReviewResponse;
import com.zaylope.registration.admin.AdminDtos.PageResponse;
import com.zaylope.registration.admin.AdminDtos.PaymentInfo;
import com.zaylope.registration.admin.AdminDtos.PaymentTransactionSummaryResponse;
import com.zaylope.registration.affiliate.AffiliateIdService;
import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantBankDetails;
import com.zaylope.registration.applicant.ApplicantBankDetailsRepository;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.common.exception.ApplicationNotSubmittedException;
import com.zaylope.registration.common.exception.PaymentRequiredForApprovalException;
import com.zaylope.registration.config.DocumentUploadProperties;
import com.zaylope.registration.document.ApplicantDocument;
import com.zaylope.registration.document.ApplicantDocumentContent;
import com.zaylope.registration.document.ApplicantDocumentRepository;
import com.zaylope.registration.document.ApplicantDocumentResponse;
import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.document.ApplicantDocumentVerificationStatus;
import com.zaylope.registration.payment.PaymentOrder;
import com.zaylope.registration.payment.PaymentOrderRepository;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminApplicationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminApplicationService.class);
	private static final long REQUIRED_FEE_PAISE = 59900L;

	private final ApplicantRepository applicantRepository;
	private final ApplicantDocumentRepository documentRepository;
	private final ApplicantBankDetailsRepository bankDetailsRepository;
	private final PaymentOrderRepository paymentOrderRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final AdminApprovalHistoryRepository approvalHistoryRepository;
	private final AffiliateMembershipRepository affiliateMembershipRepository;
	private final AffiliateIdService affiliateIdService;
	private final ApplicantDocumentService documentService;

	public AdminApplicationService(
			ApplicantRepository applicantRepository,
			ApplicantDocumentRepository documentRepository,
			ApplicantBankDetailsRepository bankDetailsRepository,
			PaymentOrderRepository paymentOrderRepository,
			PaymentTransactionRepository paymentTransactionRepository,
			AdminApprovalHistoryRepository approvalHistoryRepository,
			AffiliateMembershipRepository affiliateMembershipRepository,
			AffiliateIdService affiliateIdService,
			ApplicantDocumentService documentService,
			DocumentUploadProperties documentUploadProperties) {
		this.applicantRepository = applicantRepository;
		this.documentRepository = documentRepository;
		this.bankDetailsRepository = bankDetailsRepository;
		this.paymentOrderRepository = paymentOrderRepository;
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.approvalHistoryRepository = approvalHistoryRepository;
		this.affiliateMembershipRepository = affiliateMembershipRepository;
		this.affiliateIdService = affiliateIdService;
		this.documentService = documentService;
	}

	@Transactional(readOnly = true)
	public AdminMetricsResponse getMetrics() {
		long total = applicantRepository.count();
		long unpaid = applicantRepository.countUnpaidDrafts();
		long pendingReviews = applicantRepository.countPendingComplianceReviews();
		long paid = applicantRepository.countPaidMemberships();
		long approved = applicantRepository.countApprovedAffiliates();
		long rejected = applicantRepository.countRejectedApplications();
		long revenue = paymentTransactionRepository.sumCapturedRevenuePaise();

		return new AdminMetricsResponse(
				total,
				unpaid,
				pendingReviews,
				paid,
				approved,
				rejected,
				revenue,
				"INR",
				Instant.now());
	}

	@Transactional(readOnly = true)
	public PageResponse<ApplicantSummaryResponse> listApplicants(
			Pageable pageable,
			String status,
			String paymentStatus,
			String search) {
		String cleanStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) ? "ALL" : status.trim();
		String cleanPaymentStatus = (paymentStatus == null || paymentStatus.isBlank() || "ALL".equalsIgnoreCase(paymentStatus.trim())) ? "ALL" : paymentStatus.trim();
		String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();

		Page<Applicant> page = applicantRepository.findFiltered(cleanStatus, cleanPaymentStatus, cleanSearch, pageable);

		List<ApplicantSummaryResponse> items = page.getContent().stream()
				.map(app -> {
					int docCount = documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(app.getApplicantId()).size();
					return new ApplicantSummaryResponse(
							app.getApplicantId(),
							app.getRegistrationId(),
							app.getFullName(),
							app.getMobileNumber(),
							app.getEmail(),
							app.isMobileVerified(),
							app.isEmailVerified(),
							app.getRegistrationStatus(),
							app.getPaymentStatus(),
							app.getApprovalStatus(),
							app.getAffiliateId(),
							docCount,
							app.getRegistrationSubmittedAt(),
							app.getCreatedAt());
				})
				.toList();

		return new PageResponse<>(
				items,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}

	@Transactional(readOnly = true)
	public List<ApplicantSummaryResponse> listApplicants(String statusFilter, String search) {
		List<Applicant> applicants = applicantRepository.findAll();

		return applicants.stream()
				.filter(app -> {
					if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
						return statusFilter.equalsIgnoreCase(app.getApprovalStatus());
					}
					return true;
				})
				.filter(app -> {
					if (search != null && !search.isBlank()) {
						String q = search.trim().toLowerCase();
						return (app.getFullName() != null && app.getFullName().toLowerCase().contains(q))
								|| (app.getMobileNumber() != null && app.getMobileNumber().contains(q))
								|| (app.getEmail() != null && app.getEmail().toLowerCase().contains(q))
								|| (app.getRegistrationId() != null && app.getRegistrationId().toLowerCase().contains(q))
								|| (app.getAffiliateId() != null && app.getAffiliateId().toLowerCase().contains(q));
					}
					return true;
				})
				.sorted((a, b) -> {
					Instant ta = a.getRegistrationSubmittedAt() != null ? a.getRegistrationSubmittedAt() : a.getCreatedAt();
					Instant tb = b.getRegistrationSubmittedAt() != null ? b.getRegistrationSubmittedAt() : b.getCreatedAt();
					return tb.compareTo(ta);
				})
				.map(app -> {
					int docCount = documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(app.getApplicantId()).size();
					return new ApplicantSummaryResponse(
							app.getApplicantId(),
							app.getRegistrationId(),
							app.getFullName(),
							app.getMobileNumber(),
							app.getEmail(),
							app.isMobileVerified(),
							app.isEmailVerified(),
							app.getRegistrationStatus(),
							app.getPaymentStatus(),
							app.getApprovalStatus(),
							app.getAffiliateId(),
							docCount,
							app.getRegistrationSubmittedAt(),
							app.getCreatedAt());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public ApplicantDetailResponse getApplicantDetails(Long applicantId) {
		Applicant applicant = applicantRepository.findById(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		Optional<ApplicantBankDetails> bankOpt = bankDetailsRepository.findByApplicantId(applicantId);
		BankInfo bankInfo = bankOpt.map(b -> new BankInfo(
				b.getAccountHolderName(),
				b.getBankName(),
				b.getMaskedAccountNumber(),
				b.getIfscCode(),
				b.getConsentAt()
		)).orElse(null);

		List<ApplicantDocument> docs = documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(applicantId);
		List<DocumentInfo> docInfos = docs.stream().map(d -> new DocumentInfo(
				d.getDocumentId(),
				d.getDocumentType(),
				d.getOriginalFilename(),
				d.getFileSize(),
				d.getUploadedAt(),
				d.getVerificationStatus() != null ? d.getVerificationStatus().name() : "PENDING"
		)).toList();

		List<PaymentOrder> orders = paymentOrderRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
		List<PaymentTransaction> txs = paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
		List<PaymentInfo> paymentInfos = new ArrayList<>();
		for (PaymentTransaction tx : txs) {
			paymentInfos.add(new PaymentInfo(
					tx.getGatewayOrderId(),
					tx.getGatewayPaymentId(),
					tx.getAmountPaise(),
					tx.getCurrency(),
					tx.getStatus(),
					tx.isSignatureVerified(),
					tx.getCapturedAt()));
		}
		if (paymentInfos.isEmpty()) {
			for (PaymentOrder ord : orders) {
				paymentInfos.add(new PaymentInfo(
						ord.getGatewayOrderId(),
						null,
						ord.getAmountPaise(),
						ord.getCurrency(),
						ord.getStatus(),
						false,
						null));
			}
		}

		List<AdminApprovalHistory> history = approvalHistoryRepository.findByApplicantIdOrderByOccurredAtDesc(applicantId);
		List<ApprovalHistoryInfo> historyInfos = history.stream().map(h -> new ApprovalHistoryInfo(
				h.getApprovalId(),
				applicant.getApplicantId(),
				applicant.getFullName(),
				h.getAdminUsername(),
				h.getAction(),
				h.getReasonOrNotes(),
				h.getOccurredAt()
		)).toList();

		return new ApplicantDetailResponse(
				applicant.getApplicantId(),
				applicant.getRegistrationId(),
				applicant.getFullName(),
				applicant.getMobileNumber(),
				applicant.getEmail(),
				applicant.isMobileVerified(),
				applicant.isEmailVerified(),
				applicant.getRegistrationStatus(),
				applicant.getPaymentStatus(),
				applicant.getApprovalStatus(),
				applicant.getAffiliateId(),
				applicant.getRejectionReason(),
				applicant.getApprovedAt(),
				applicant.getRejectedAt(),
				applicant.getRegistrationSubmittedAt(),
				applicant.getCreatedAt(),
				bankInfo,
				docInfos,
				paymentInfos,
				historyInfos);
	}

	@Transactional
	public AdminApprovalResponse approveApplicant(Long applicantId, String adminUsername, String notes) {
		// Concurrency protection: acquire pessimistic write lock
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		// Idempotency: return existing approval details if already approved
		if ("APPROVED".equalsIgnoreCase(applicant.getApprovalStatus()) && applicant.getAffiliateId() != null) {
			Optional<AffiliateMembership> existing = affiliateMembershipRepository.findByApplicantId(applicantId);
			String refCode = existing.map(AffiliateMembership::getReferralCode).orElse("ZP" + applicant.getAffiliateId().replace("-", ""));
			return new AdminApprovalResponse(
					applicantId,
					"APPROVED",
					applicant.getAffiliateId(),
					refCode,
					adminUsername,
					applicant.getApprovedAt());
		}

		// Gate 1: Rejection invariant
		if ("REJECTED".equalsIgnoreCase(applicant.getApprovalStatus())) {
			throw new IllegalStateException("Cannot approve application. The application has already been rejected.");
		}

		// Gate 2: Submission invariant
		if (applicant.getRegistrationSubmittedAt() == null || !"SUBMITTED".equalsIgnoreCase(applicant.getRegistrationStatus())) {
			throw new ApplicationNotSubmittedException();
		}

		// Gate 3: Contact verification invariant (mobile OR email verified)
		if (!applicant.isContactVerified()) {
			throw new IllegalStateException("Cannot approve application. Applicant must have a verified mobile number or email address.");
		}

		// Gate 4: Payment invariant (₹599 verified and captured)
		if (!applicant.isPaymentPaid()) {
			LOGGER.warn("Attempted to approve applicantId {} without confirmed payment", applicantId);
			throw new PaymentRequiredForApprovalException("Cannot approve application. The required membership payment of ₹599 has not been confirmed.");
		}

		List<PaymentTransaction> capturedTxs = paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId)
				.stream()
				.filter(tx -> "CAPTURED".equalsIgnoreCase(tx.getStatus()) && tx.isSignatureVerified() && tx.getAmountPaise() == REQUIRED_FEE_PAISE)
				.toList();
		if (capturedTxs.isEmpty()) {
			LOGGER.warn("Applicant {} has payment_status PAID but no captured verified ₹599 transaction in database", applicantId);
			throw new PaymentRequiredForApprovalException("Cannot approve application. No captured, verified ₹599 payment transaction found for applicant.");
		}

		Instant now = Instant.now();

		// Reuse existing permanent identity from affiliate_memberships if already activated by payment
		Optional<AffiliateMembership> existingMembershipOpt = affiliateMembershipRepository.findByApplicantId(applicantId);
		String affiliateId;
		String referralCode;
		if (existingMembershipOpt.isPresent()) {
			AffiliateMembership existingMembership = existingMembershipOpt.get();
			affiliateId = existingMembership.getAffiliateId();
			referralCode = existingMembership.getReferralCode();
		} else {
			affiliateId = affiliateIdService.generateNextAffiliateId();
			referralCode = affiliateIdService.generateUniqueReferralCode();
		}

		// Record approval history
		AdminApprovalHistory history = new AdminApprovalHistory(
				applicantId,
				adminUsername,
				"APPROVED",
				notes != null && !notes.isBlank() ? notes.trim() : "Approved after contact verification and payment confirmation.");
		approvalHistoryRepository.save(history);

		if (existingMembershipOpt.isEmpty()) {
			AffiliateMembership membership = new AffiliateMembership(
					applicantId,
					affiliateId,
					referralCode,
					history.getApprovalId(),
					now);
			affiliateMembershipRepository.save(membership);
		}

		// Mark applicant approved
		applicant.markApproved(affiliateId, now);
		applicantRepository.save(applicant);

		LOGGER.info("Applicant {} successfully approved by admin {}. Assigned Affiliate ID: {}",
				applicantId, adminUsername, affiliateId);

		return new AdminApprovalResponse(
				applicantId,
				"APPROVED",
				affiliateId,
				referralCode,
				adminUsername,
				now);
	}

	@Transactional
	public AdminRejectionResponse rejectApplicant(Long applicantId, String adminUsername, String reason) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		Instant now = Instant.now();
		String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : "Application rejected during compliance review.";

		AdminApprovalHistory history = new AdminApprovalHistory(
				applicantId,
				adminUsername,
				"REJECTED",
				cleanReason);
		approvalHistoryRepository.save(history);

		applicant.markRejected(cleanReason, now);
		applicantRepository.save(applicant);

		LOGGER.info("Applicant {} rejected by admin {}. Reason: {}", applicantId, adminUsername, cleanReason);

		return new AdminRejectionResponse(
				applicantId,
				"REJECTED",
				cleanReason,
				adminUsername,
				now);
	}

	@Transactional
	public DocumentReviewResponse reviewDocument(
			Long applicantId,
			String documentId,
			String status,
			String rejectionReason,
			String reviewerRole) {
		ApplicantDocumentVerificationStatus newStatus;
		try {
			newStatus = ApplicantDocumentVerificationStatus.valueOf(status.trim().toUpperCase());
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid document verification status: " + status);
		}

		ApplicantDocumentResponse updated = documentService.reviewDocument(
				applicantId,
				documentId,
				newStatus,
				reviewerRole != null ? reviewerRole : "ADMIN",
				rejectionReason);

		return new DocumentReviewResponse(
				updated.documentId(),
				updated.documentType(),
				updated.verificationStatus(),
				reviewerRole != null ? reviewerRole : "ADMIN",
				Instant.now());
	}

	@Transactional
	public ApplicantDocumentContent getDocumentContent(Long applicantId, String documentId, String adminUsername) {
		return documentService.adminContent(applicantId, documentId, adminUsername);
	}

	@Transactional(readOnly = true)
	public PageResponse<PaymentTransactionSummaryResponse> listPayments(Pageable pageable, String status) {
		String cleanStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) ? "ALL" : status.trim();
		Page<PaymentTransaction> page = paymentTransactionRepository.findFiltered(cleanStatus, pageable);

		List<PaymentTransactionSummaryResponse> items = page.getContent().stream()
				.map(tx -> {
			String applicantName = applicantRepository.findById(tx.getApplicantId())
					.map(Applicant::getFullName)
					.orElse("Applicant #" + tx.getApplicantId());
			return new PaymentTransactionSummaryResponse(
					tx.getTransactionId(),
					tx.getApplicantId(),
					applicantName,
					tx.getGatewayOrderId(),
					tx.getGatewayPaymentId(),
					tx.getAmountPaise(),
					tx.getCurrency(),
					tx.getStatus(),
					tx.isSignatureVerified(),
					tx.getCapturedAt(),
					tx.getCreatedAt());
		}).toList();

		return new PageResponse<>(
				items,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}

	@Transactional(readOnly = true)
	public PageResponse<AffiliateSummaryResponse> listAffiliates(Pageable pageable, String search) {
		String cleanSearch = (search == null || search.isBlank()) ? null : search.trim();
		Page<AffiliateMembership> page = affiliateMembershipRepository.findFiltered(cleanSearch, pageable);

		List<AffiliateSummaryResponse> items = page.getContent().stream()
				.map(m -> {
			Applicant app = applicantRepository.findById(m.getApplicantId()).orElse(null);
			String name = app != null ? app.getFullName() : "Affiliate #" + m.getApplicantId();
			String email = app != null ? app.getEmail() : "";
			return new AffiliateSummaryResponse(
					m.getMembershipId(),
					m.getApplicantId(),
					name,
					email,
					m.getAffiliateId(),
					m.getReferralCode(),
					m.getStatus(),
					m.getTier(),
					m.getActivatedAt());
		}).toList();

		return new PageResponse<>(
				items,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}

	@Transactional(readOnly = true)
	public PageResponse<ApprovalHistoryInfo> listAuditLogs(Pageable pageable) {
		Page<AdminApprovalHistory> page = approvalHistoryRepository.findAllByOrderByOccurredAtDesc(pageable);

		List<ApprovalHistoryInfo> items = page.getContent().stream()
				.map(h -> {
			String applicantName = applicantRepository.findById(h.getApplicantId())
					.map(Applicant::getFullName)
					.orElse("Applicant #" + h.getApplicantId());
			return new ApprovalHistoryInfo(
					h.getApprovalId(),
					h.getApplicantId(),
					applicantName,
					h.getAdminUsername(),
					h.getAction(),
					h.getReasonOrNotes(),
					h.getOccurredAt());
		}).toList();

		return new PageResponse<>(
				items,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isFirst(),
				page.isLast());
	}
}
