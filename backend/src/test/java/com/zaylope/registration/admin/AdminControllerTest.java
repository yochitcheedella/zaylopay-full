package com.zaylope.registration.admin;

import com.zaylope.registration.admin.AdminDtos.AdminApprovalResponse;
import com.zaylope.registration.admin.AdminDtos.AdminMetricsResponse;
import com.zaylope.registration.admin.AdminDtos.AdminRejectionResponse;
import com.zaylope.registration.admin.AdminDtos.AffiliateSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantDetailResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApprovalHistoryInfo;
import com.zaylope.registration.admin.AdminDtos.DocumentReviewResponse;
import com.zaylope.registration.admin.AdminDtos.PageResponse;
import com.zaylope.registration.admin.AdminDtos.PaymentTransactionSummaryResponse;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.document.ApplicantDocumentContent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

	private AdminSessionService adminSessionService;
	private AdminApplicationService adminApplicationService;
	private AdminUserRepository adminUserRepository;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		adminSessionService = Mockito.mock(AdminSessionService.class);
		adminApplicationService = Mockito.mock(AdminApplicationService.class);
		adminUserRepository = Mockito.mock(AdminUserRepository.class);

		AdminController controller = new AdminController(
				adminSessionService,
				adminApplicationService,
				adminUserRepository,
				false);

		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void loginShouldAuthenticateAndSetHttpOnlyCookie() throws Exception {
		AdminUser admin = new AdminUser(1L, "superadmin", "admin@zaylope.com", "hash", "Super Admin", "SUPER_ADMIN");
		when(adminSessionService.login(eq("superadmin"), eq("password123"), any())).thenReturn("mock_session_token_32_bytes_long_hex_string");
		when(adminSessionService.findActiveAdmin("mock_session_token_32_bytes_long_hex_string")).thenReturn(Optional.of(admin));

		mockMvc.perform(post("/api/v1/admin/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"username": "superadmin",
									"password": "password123"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", containsString("ZAYLOPE_ADMIN=mock_session_token_32_bytes_long_hex_string")))
				.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
				.andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/admin")))
				.andExpect(jsonPath("$.username", is("superadmin")))
				.andExpect(jsonPath("$.role", is("SUPER_ADMIN")));
	}

	@Test
	void getMetricsShouldReturnRealCountsAndRevenue() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		AdminMetricsResponse metrics = new AdminMetricsResponse(
				50L, 10L, 5L, 35L, 30L, 2L, 1797000L, "INR", Instant.now());
		when(adminApplicationService.getMetrics()).thenReturn(metrics);

		mockMvc.perform(get("/api/v1/admin/metrics")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalApplicants", is(50)))
				.andExpect(jsonPath("$.unpaidDrafts", is(10)))
				.andExpect(jsonPath("$.pendingComplianceReviews", is(5)))
				.andExpect(jsonPath("$.paidMemberships", is(35)))
				.andExpect(jsonPath("$.approvedAffiliates", is(30)))
				.andExpect(jsonPath("$.totalCapturedRevenuePaise", is(1797000)))
				.andExpect(jsonPath("$.currency", is("INR")));
	}

	@Test
	void listApplicantsShouldReturnPaginatedResults() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		ApplicantSummaryResponse applicantSummary = new ApplicantSummaryResponse(
				12L, "APP-12", "Sai Sudheer", "+919059960174", "sai189@gmail.com",
				true, true, "SUBMITTED", "PAID", "PENDING", null, 1, Instant.now(), Instant.now());
		PageResponse<ApplicantSummaryResponse> pageResponse = new PageResponse<>(
				List.of(applicantSummary), 0, 20, 1L, 1, true, true);

		when(adminApplicationService.listApplicants(any(), eq("ALL"), eq("ALL"), eq(null))).thenReturn(pageResponse);

		mockMvc.perform(get("/api/v1/admin/applicants")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].applicantId", is(12)))
				.andExpect(jsonPath("$.content[0].fullName", is("Sai Sudheer")))
				.andExpect(jsonPath("$.content[0].paymentStatus", is("PAID")))
				.andExpect(jsonPath("$.content[0].approvalStatus", is("PENDING")));
	}

	@Test
	void getApplicantDetailsShouldReturnComprehensiveData() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		ApplicantDetailResponse details = new ApplicantDetailResponse(
				12L, "APP-12", "Sai Sudheer", "+919059960174", "sai189@gmail.com",
				true, true, "SUBMITTED", "PAID", "PENDING", null, null, null, null,
				Instant.now(), Instant.now(), null, List.of(), List.of(), List.of());

		when(adminApplicationService.getApplicantDetails(12L)).thenReturn(details);

		mockMvc.perform(get("/api/v1/admin/applicants/12")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantId", is(12)))
				.andExpect(jsonPath("$.fullName", is("Sai Sudheer")));
	}

	@Test
	void approveShouldGenerateAffiliateId() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		AdminApprovalResponse approvalResponse = new AdminApprovalResponse(
				12L, "APPROVED", "ZP-261124-001", "ZP8K29MA", "superadmin", Instant.now());

		when(adminApplicationService.approveApplicant(12L, "superadmin", "All checks verified"))
				.thenReturn(approvalResponse);

		mockMvc.perform(post("/api/v1/admin/applicants/12/approve")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"notes": "All checks verified"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantId", is(12)))
				.andExpect(jsonPath("$.approvalStatus", is("APPROVED")))
				.andExpect(jsonPath("$.affiliateId", is("ZP-261124-001")))
				.andExpect(jsonPath("$.referralCode", is("ZP8K29MA")));
	}

	@Test
	void rejectShouldRecordRejectionReason() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		AdminRejectionResponse rejectionResponse = new AdminRejectionResponse(
				12L, "REJECTED", "PAN name mismatch", "superadmin", Instant.now());

		when(adminApplicationService.rejectApplicant(12L, "superadmin", "PAN name mismatch"))
				.thenReturn(rejectionResponse);

		mockMvc.perform(post("/api/v1/admin/applicants/12/reject")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"reason": "PAN name mismatch"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantId", is(12)))
				.andExpect(jsonPath("$.approvalStatus", is("REJECTED")))
				.andExpect(jsonPath("$.rejectionReason", is("PAN name mismatch")));
	}

	@Test
	void getDocumentContentShouldReturnContentWithSecurityHeaders() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		byte[] mockBytes = "sample image bytes".getBytes();
		ApplicantDocumentContent content = new ApplicantDocumentContent(mockBytes, "image/jpeg", "pan_card.jpg");

		when(adminApplicationService.getDocumentContent(12L, "doc-123", "superadmin"))
				.thenReturn(content);

		mockMvc.perform(get("/api/v1/admin/applicants/12/documents/doc-123/content")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", is("image/jpeg")))
				.andExpect(header().string("Content-Disposition", is("inline; filename=\"pan_card.jpg\"")))
				.andExpect(header().string("Cache-Control", is("no-store")))
				.andExpect(header().string("X-Content-Type-Options", is("nosniff")))
				.andExpect(header().string("Content-Security-Policy", is("default-src 'none'")));
	}

	@Test
	void reviewDocumentShouldUpdateStatus() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		DocumentReviewResponse response = new DocumentReviewResponse(
				"doc-123", "PAN Card", "APPROVED", "SUPER_ADMIN", Instant.now());

		when(adminApplicationService.reviewDocument(12L, "doc-123", "APPROVED", null, "SUPER_ADMIN"))
				.thenReturn(response);

		mockMvc.perform(post("/api/v1/admin/applicants/12/documents/doc-123/review")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
									"status": "APPROVED"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documentId", is("doc-123")))
				.andExpect(jsonPath("$.verificationStatus", is("APPROVED")));
	}

	@Test
	void listPaymentsShouldReturnPaginatedTransactions() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		PaymentTransactionSummaryResponse tx = new PaymentTransactionSummaryResponse(
				1L, 12L, "Sai Sudheer", "order_123", "pay_456", 59900L, "INR", "CAPTURED", true, Instant.now(), Instant.now());
		PageResponse<PaymentTransactionSummaryResponse> page = new PageResponse<>(List.of(tx), 0, 20, 1L, 1, true, true);

		when(adminApplicationService.listPayments(any(), eq("ALL"))).thenReturn(page);

		mockMvc.perform(get("/api/v1/admin/payments")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].gatewayOrderId", is("order_123")))
				.andExpect(jsonPath("$.content[0].amountPaise", is(59900)));
	}

	@Test
	void listAffiliatesShouldReturnPaginatedAffiliates() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		AffiliateSummaryResponse aff = new AffiliateSummaryResponse(
				1L, 12L, "Sai Sudheer", "sai@example.com", "ZP-261124-001", "ZP8K29MA", "ACTIVE", "STANDARD", Instant.now());
		PageResponse<AffiliateSummaryResponse> page = new PageResponse<>(List.of(aff), 0, 20, 1L, 1, true, true);

		when(adminApplicationService.listAffiliates(any(), eq(null))).thenReturn(page);

		mockMvc.perform(get("/api/v1/admin/affiliates")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].affiliateId", is("ZP-261124-001")))
				.andExpect(jsonPath("$.content[0].referralCode", is("ZP8K29MA")));
	}

	@Test
	void listAuditLogsShouldReturnPaginatedLogs() throws Exception {
		AdminPrincipal principal = new AdminPrincipal(1L, "superadmin", "SUPER_ADMIN");
		ApprovalHistoryInfo log = new ApprovalHistoryInfo(
				1L, 12L, "Sai Sudheer", "superadmin", "APPROVED", "Approved", Instant.now());
		PageResponse<ApprovalHistoryInfo> page = new PageResponse<>(List.of(log), 0, 20, 1L, 1, true, true);

		when(adminApplicationService.listAuditLogs(any())).thenReturn(page);

		mockMvc.perform(get("/api/v1/admin/audit-logs")
						.requestAttr(AdminPrincipal.REQUEST_ATTRIBUTE, principal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].action", is("APPROVED")))
				.andExpect(jsonPath("$.content[0].adminUsername", is("superadmin")));
	}
}
