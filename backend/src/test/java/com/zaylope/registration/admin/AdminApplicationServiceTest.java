package com.zaylope.registration.admin;

import com.zaylope.registration.admin.AdminDtos.AdminApprovalResponse;
import com.zaylope.registration.admin.AdminDtos.AdminMetricsResponse;
import com.zaylope.registration.admin.AdminDtos.AdminRejectionResponse;
import com.zaylope.registration.admin.AdminDtos.DocumentReviewResponse;
import com.zaylope.registration.affiliate.AffiliateIdService;
import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantBankDetails;
import com.zaylope.registration.applicant.ApplicantBankDetailsRepository;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicationNotSubmittedException;
import com.zaylope.registration.common.exception.PaymentRequiredForApprovalException;
import com.zaylope.registration.config.DocumentUploadProperties;
import com.zaylope.registration.document.ApplicantDocument;
import com.zaylope.registration.document.ApplicantDocumentRepository;
import com.zaylope.registration.document.ApplicantDocumentResponse;
import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.document.ApplicantDocumentVerificationStatus;
import com.zaylope.registration.payment.PaymentOrderRepository;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminApplicationServiceTest {

	private ApplicantRepository applicantRepository;
	private ApplicantDocumentRepository documentRepository;
	private ApplicantBankDetailsRepository bankDetailsRepository;
	private PaymentOrderRepository paymentOrderRepository;
	private PaymentTransactionRepository paymentTransactionRepository;
	private AdminApprovalHistoryRepository approvalHistoryRepository;
	private AffiliateMembershipRepository affiliateMembershipRepository;
	private AffiliateIdService affiliateIdService;
	private ApplicantDocumentService documentService;
	private DocumentUploadProperties documentUploadProperties;
	private AdminApplicationService service;

	@BeforeEach
	void setUp() {
		applicantRepository = Mockito.mock(ApplicantRepository.class);
		documentRepository = Mockito.mock(ApplicantDocumentRepository.class);
		bankDetailsRepository = Mockito.mock(ApplicantBankDetailsRepository.class);
		paymentOrderRepository = Mockito.mock(PaymentOrderRepository.class);
		paymentTransactionRepository = Mockito.mock(PaymentTransactionRepository.class);
		approvalHistoryRepository = Mockito.mock(AdminApprovalHistoryRepository.class);
		affiliateMembershipRepository = Mockito.mock(AffiliateMembershipRepository.class);
		affiliateIdService = Mockito.mock(AffiliateIdService.class);
		documentService = Mockito.mock(ApplicantDocumentService.class);
		documentUploadProperties = Mockito.mock(DocumentUploadProperties.class);

		when(documentUploadProperties.requiredTypes()).thenReturn(List.of("PAN Card"));

		service = new AdminApplicationService(
				applicantRepository,
				documentRepository,
				bankDetailsRepository,
				paymentOrderRepository,
				paymentTransactionRepository,
				approvalHistoryRepository,
				affiliateMembershipRepository,
				affiliateIdService,
				documentService,
				documentUploadProperties);
	}

	private Applicant createFullyPreparedApplicant(Long id) {
		Applicant applicant = new Applicant("Sai Sudheer", "+919876543210", "sai@example.com");
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.markEmailVerified(now);
		applicant.markRegistrationSubmitted(now);
		applicant.markPaymentPaid();
		return applicant;
	}

	private ApplicantBankDetails createSampleBankDetails(Long applicantId) {
		return new ApplicantBankDetails(
				applicantId,
				"Sai Sudheer",
				"State Bank of India",
				"encrypted_acct_blob",
				"1234",
				"••••••••1234",
				"SBIN0001234");
	}

	private ApplicantDocument createApprovedPanDocument(Applicant applicant) {
		ApplicantDocument doc = new ApplicantDocument(
				"doc-pan-uuid",
				applicant,
				"PAN Card",
				"pan-storage-id",
				"pan.jpg",
				"image/jpeg",
				1024L,
				Instant.now(),
				Instant.now(),
				"1.0");
		doc.updateVerificationStatus(ApplicantDocumentVerificationStatus.APPROVED);
		return doc;
	}

	private PaymentTransaction createCapturedTransaction(Long applicantId) {
		return new PaymentTransaction(
				101L,
				applicantId,
				"pay_test_123",
				"order_test_456",
				59900L,
				"INR",
				"upi",
				"CAPTURED",
				"valid_sig",
				true);
	}

	@Test
	void shouldBlockApprovalIfPaymentIsNotPaid() {
		Applicant applicant = new Applicant("Sai Sudheer", "+919876543210", "sai@example.com");
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.markEmailVerified(now);
		applicant.markRegistrationSubmitted(now);
		// Payment is PENDING by default
		assertFalse(applicant.isPaymentPaid());

		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));

		assertThrows(PaymentRequiredForApprovalException.class, () ->
				service.approveApplicant(1L, "admin", "Approved"));
	}

	@Test
	void shouldBlockApprovalIfNoCapturedPaymentTransactionExists() {
		Applicant applicant = createFullyPreparedApplicant(1L);
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

		assertThrows(PaymentRequiredForApprovalException.class, () ->
				service.approveApplicant(1L, "admin", "Approved"));
	}

	@Test
	void shouldBlockApprovalIfApplicationNotSubmitted() {
		Applicant applicant = new Applicant("Sai Sudheer", "+919876543210", "sai@example.com");
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.markEmailVerified(now);
		// Not submitted
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));

		assertThrows(ApplicationNotSubmittedException.class, () ->
				service.approveApplicant(1L, "admin", "Approved"));
	}

	@Test
	void shouldBlockApprovalIfContactsNotVerified() {
		Applicant applicant = new Applicant("Sai Sudheer", "+919876543210", "sai@example.com");
		Instant now = Instant.now();
		applicant.markRegistrationSubmitted(now);
		// Contacts not verified
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));

		assertThrows(IllegalStateException.class, () ->
				service.approveApplicant(1L, "admin", "Approved"));
	}

	@Test
	void shouldApproveMobileOnlyApplicantWithoutDocumentsOrBankDetails() {
		Applicant applicant = new Applicant("Mobile Only", "+919876543210", "mobileonly@example.com");
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.recordPrivacyConsent(now, "onboarding-v1");
		applicant.markRegistrationSubmitted(now);
		applicant.markPaymentPaid();

		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(1L))
				.thenReturn(List.of(createCapturedTransaction(1L)));
		when(affiliateIdService.generateNextAffiliateId()).thenReturn("ZP-261124-002");
		when(affiliateIdService.generateUniqueReferralCode()).thenReturn("ZPMOB123");

		AdminApprovalResponse res = service.approveApplicant(1L, "admin", "Approved without KYC/bank");

		assertNotNull(res);
		assertEquals("APPROVED", res.approvalStatus());
		assertEquals("ZP-261124-002", res.affiliateId());
		assertFalse(applicant.isEmailVerified());
	}

	@Test
	void shouldApproveEmailOnlyApplicantWithoutMobileDocumentsOrBankDetails() {
		Applicant applicant = new Applicant("Email Only", "+919876543211", "emailonly@example.com");
		Instant now = Instant.now();
		applicant.markEmailVerified(now);
		applicant.recordPrivacyConsent(now, "onboarding-v1");
		applicant.markRegistrationSubmitted(now);
		applicant.markPaymentPaid();

		when(applicantRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(applicant));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(2L))
				.thenReturn(List.of(createCapturedTransaction(2L)));
		when(affiliateIdService.generateNextAffiliateId()).thenReturn("ZP-261124-003");
		when(affiliateIdService.generateUniqueReferralCode()).thenReturn("ZPEML123");

		AdminApprovalResponse res = service.approveApplicant(2L, "admin", "Approved via email verification");

		assertNotNull(res);
		assertEquals("APPROVED", res.approvalStatus());
		assertEquals("ZP-261124-003", res.affiliateId());
		assertFalse(applicant.isMobileVerified());
	}

	@Test
	void shouldBlockApprovalIfApplicantAlreadyRejected() {
		Applicant applicant = createFullyPreparedApplicant(1L);
		applicant.markRejected("Invalid ID", Instant.now());
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));

		assertThrows(IllegalStateException.class, () ->
				service.approveApplicant(1L, "admin", "Approved"));
	}

	@Test
	void shouldReturnExistingApprovalIdempotentlyIfAlreadyApproved() {
		Applicant applicant = createFullyPreparedApplicant(1L);
		applicant.markApproved("ZP-261124-001", Instant.now());

		AffiliateMembership membership = new AffiliateMembership(
				1L,
				"ZP-261124-001",
				"ZPABC123",
				10L,
				Instant.now());

		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(affiliateMembershipRepository.findByApplicantId(1L)).thenReturn(Optional.of(membership));

		AdminApprovalResponse res = service.approveApplicant(1L, "admin", "Already approved");

		assertNotNull(res);
		assertEquals("APPROVED", res.approvalStatus());
		assertEquals("ZP-261124-001", res.affiliateId());
		assertEquals("ZPABC123", res.referralCode());

		verify(affiliateIdService, never()).generateNextAffiliateId();
		verify(approvalHistoryRepository, never()).save(any());
	}

	@Test
	void shouldApproveAndGenerateAffiliateIdWhenAllGatesPass() {
		Applicant applicant = createFullyPreparedApplicant(1L);
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(1L))
				.thenReturn(List.of(createCapturedTransaction(1L)));
		when(documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(1L))
				.thenReturn(List.of(createApprovedPanDocument(applicant)));
		when(bankDetailsRepository.findByApplicantId(1L))
				.thenReturn(Optional.of(createSampleBankDetails(1L)));

		when(affiliateIdService.generateNextAffiliateId()).thenReturn("ZP-261124-001");
		when(affiliateIdService.generateUniqueReferralCode()).thenReturn("ZP8K29MA");

		AdminApprovalResponse res = service.approveApplicant(1L, "superadmin", "All checks passed");

		assertNotNull(res);
		assertEquals("APPROVED", res.approvalStatus());
		assertEquals("ZP-261124-001", res.affiliateId());
		assertEquals("ZP8K29MA", res.referralCode());
		assertEquals("ZP-261124-001", applicant.getAffiliateId());
		assertEquals("APPROVED", applicant.getApprovalStatus());

		verify(approvalHistoryRepository).save(any());
		verify(affiliateMembershipRepository).save(any(AffiliateMembership.class));
		verify(applicantRepository).save(applicant);
	}

	@Test
	void shouldRejectApplicantAndRecordReason() {
		Applicant applicant = new Applicant("Sai Sudheer", "+919876543210", "sai@example.com");
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));

		AdminRejectionResponse res = service.rejectApplicant(1L, "admin", "Name does not match PAN card");

		assertNotNull(res);
		assertEquals("REJECTED", res.approvalStatus());
		assertEquals("Name does not match PAN card", res.rejectionReason());
		assertEquals("REJECTED", applicant.getApprovalStatus());

		verify(approvalHistoryRepository).save(any());
		verify(applicantRepository).save(applicant);
	}

	@Test
	void shouldReviewDocumentSuccessfully() {
		ApplicantDocumentResponse docResponse = new ApplicantDocumentResponse(
				"doc-123",
				"PAN Card",
				"pan.png",
				"image/png",
				2048L,
				Instant.now(),
				"APPROVED");

		when(documentService.reviewDocument(
				eq(1L),
				eq("doc-123"),
				eq(ApplicantDocumentVerificationStatus.APPROVED),
				eq("SUPER_ADMIN"),
				eq(null)))
				.thenReturn(docResponse);

		DocumentReviewResponse response = service.reviewDocument(1L, "doc-123", "APPROVED", null, "SUPER_ADMIN");

		assertNotNull(response);
		assertEquals("doc-123", response.documentId());
		assertEquals("APPROVED", response.verificationStatus());
		assertEquals("SUPER_ADMIN", response.reviewerRole());
	}

	@Test
	void shouldCalculateAdminMetricsAccurately() {
		when(applicantRepository.count()).thenReturn(25L);
		when(applicantRepository.countUnpaidDrafts()).thenReturn(5L);
		when(applicantRepository.countPendingComplianceReviews()).thenReturn(8L);
		when(applicantRepository.countPaidMemberships()).thenReturn(15L);
		when(applicantRepository.countApprovedAffiliates()).thenReturn(10L);
		when(applicantRepository.countRejectedApplications()).thenReturn(2L);
		when(paymentTransactionRepository.sumCapturedRevenuePaise()).thenReturn(599000L);

		AdminMetricsResponse metrics = service.getMetrics();

		assertNotNull(metrics);
		assertEquals(25L, metrics.totalApplicants());
		assertEquals(5L, metrics.unpaidDrafts());
		assertEquals(8L, metrics.pendingComplianceReviews());
		assertEquals(15L, metrics.paidMemberships());
		assertEquals(10L, metrics.approvedAffiliates());
		assertEquals(2L, metrics.rejectedApplications());
		assertEquals(599000L, metrics.totalCapturedRevenuePaise());
		assertEquals("INR", metrics.currency());
	}
}
