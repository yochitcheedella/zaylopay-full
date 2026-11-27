package com.zaylope.registration.admin;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public class AdminDtos {

	public record AdminLoginRequest(
			@NotBlank(message = "Username is required")
			String username,

			@NotBlank(message = "Password is required")
			String password) {
	}

	public record AdminMeResponse(
			long adminId,
			String username,
			String email,
			String fullName,
			String role) {
	}

	public record PageResponse<T>(
			List<T> content,
			int pageNumber,
			int pageSize,
			long totalElements,
			int totalPages,
			boolean first,
			boolean last) {

		public static <T> PageResponse<T> from(Page<T> page) {
			return new PageResponse<>(
					page.getContent(),
					page.getNumber(),
					page.getSize(),
					page.getTotalElements(),
					page.getTotalPages(),
					page.isFirst(),
					page.isLast());
		}
	}

	public record AdminMetricsResponse(
			long totalApplicants,
			long unpaidDrafts,
			long pendingComplianceReviews,
			long paidMemberships,
			long approvedAffiliates,
			long rejectedApplications,
			long totalCapturedRevenuePaise,
			String currency,
			Instant generatedAt) {
	}

	public record ApplicantSummaryResponse(
			Long applicantId,
			String registrationId,
			String fullName,
			String mobileNumber,
			String email,
			boolean mobileVerified,
			boolean emailVerified,
			String registrationStatus,
			String paymentStatus,
			String approvalStatus,
			String affiliateId,
			int documentsCount,
			Instant submittedAt,
			Instant createdAt) {
	}

	public record ApplicantDetailResponse(
			Long applicantId,
			String registrationId,
			String fullName,
			String mobileNumber,
			String email,
			boolean mobileVerified,
			boolean emailVerified,
			String registrationStatus,
			String paymentStatus,
			String approvalStatus,
			String affiliateId,
			String rejectionReason,
			Instant approvedAt,
			Instant rejectedAt,
			Instant submittedAt,
			Instant createdAt,
			BankInfo bankDetails,
			List<DocumentInfo> documents,
			List<PaymentInfo> payments,
			List<ApprovalHistoryInfo> approvalHistory) {
	}

	public record BankInfo(
			String accountHolderName,
			String bankName,
			String maskedAccountNumber,
			String ifscCode,
			Instant consentAt) {
	}

	public record DocumentInfo(
			String documentId,
			String documentType,
			String originalFilename,
			long fileSize,
			Instant uploadedAt,
			String verificationStatus) {
	}

	public record DocumentReviewRequest(
			@NotBlank(message = "Review status is required")
			String status,

			String rejectionReason) {
	}

	public record DocumentReviewResponse(
			String documentId,
			String documentType,
			String verificationStatus,
			String reviewerRole,
			Instant reviewedAt) {
	}

	public record PaymentInfo(
			String gatewayOrderId,
			String gatewayPaymentId,
			long amountPaise,
			String currency,
			String status,
			boolean signatureVerified,
			Instant capturedAt) {
	}

	public record PaymentTransactionSummaryResponse(
			Long transactionId,
			Long applicantId,
			String applicantName,
			String gatewayOrderId,
			String gatewayPaymentId,
			long amountPaise,
			String currency,
			String status,
			boolean signatureVerified,
			Instant capturedAt,
			Instant createdAt) {
	}

	public record AffiliateSummaryResponse(
			Long membershipId,
			Long applicantId,
			String applicantName,
			String applicantEmail,
			String affiliateId,
			String referralCode,
			String status,
			String tier,
			Instant activatedAt) {
	}

	public record ApprovalHistoryInfo(
			Long approvalId,
			Long applicantId,
			String applicantName,
			String adminUsername,
			String action,
			String reasonOrNotes,
			Instant occurredAt) {
	}

	public record AdminApprovalRequest(
			String notes) {
	}

	public record AdminApprovalResponse(
			Long applicantId,
			String approvalStatus,
			String affiliateId,
			String referralCode,
			String adminUsername,
			Instant approvedAt) {
	}

	public record AdminRejectionRequest(
			@NotBlank(message = "Rejection reason is required")
			String reason) {
	}

	public record AdminRejectionResponse(
			Long applicantId,
			String approvalStatus,
			String rejectionReason,
			String adminUsername,
			Instant rejectedAt) {
	}
}
