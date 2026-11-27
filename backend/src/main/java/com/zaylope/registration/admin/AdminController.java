package com.zaylope.registration.admin;

import com.zaylope.registration.admin.AdminDtos.AdminApprovalRequest;
import com.zaylope.registration.admin.AdminDtos.AdminApprovalResponse;
import com.zaylope.registration.admin.AdminDtos.AdminLoginRequest;
import com.zaylope.registration.admin.AdminDtos.AdminMeResponse;
import com.zaylope.registration.admin.AdminDtos.AdminMetricsResponse;
import com.zaylope.registration.admin.AdminDtos.AdminRejectionRequest;
import com.zaylope.registration.admin.AdminDtos.AdminRejectionResponse;
import com.zaylope.registration.admin.AdminDtos.AffiliateSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantDetailResponse;
import com.zaylope.registration.admin.AdminDtos.ApplicantSummaryResponse;
import com.zaylope.registration.admin.AdminDtos.ApprovalHistoryInfo;
import com.zaylope.registration.admin.AdminDtos.DocumentReviewRequest;
import com.zaylope.registration.admin.AdminDtos.DocumentReviewResponse;
import com.zaylope.registration.admin.AdminDtos.PageResponse;
import com.zaylope.registration.admin.AdminDtos.PaymentTransactionSummaryResponse;
import com.zaylope.registration.document.ApplicantDocumentContent;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final AdminSessionService adminSessionService;
	private final AdminApplicationService adminApplicationService;
	private final AdminUserRepository adminUserRepository;
	private final boolean cookieSecure;

	public AdminController(
			AdminSessionService adminSessionService,
			AdminApplicationService adminApplicationService,
			AdminUserRepository adminUserRepository,
			@Value("${app.security.admin.cookie-secure:false}") boolean cookieSecure) {
		this.adminSessionService = adminSessionService;
		this.adminApplicationService = adminApplicationService;
		this.adminUserRepository = adminUserRepository;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/auth/login")
	public ResponseEntity<AdminMeResponse> login(
			@Valid @RequestBody AdminLoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse response) {
		String clientIp = extractClientIp(httpRequest);
		String sessionToken = adminSessionService.login(request.username(), request.password(), clientIp);

		AdminUser admin = adminSessionService.findActiveAdmin(sessionToken).orElseThrow();

		ResponseCookie cookie = ResponseCookie.from(AdminSessionFilter.ADMIN_COOKIE_NAME, sessionToken)
				.httpOnly(true)
				.secure(cookieSecure || httpRequest.isSecure())
				.sameSite("Lax")
				.path("/api/v1/admin")
				.maxAge(Duration.ofHours(8))
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return ResponseEntity.ok(new AdminMeResponse(
				admin.getAdminId() != null ? admin.getAdminId() : 0L,
				admin.getUsername(),
				admin.getEmail(),
				admin.getFullName(),
				admin.getRole()));
	}

	@GetMapping("/auth/me")
	public ResponseEntity<AdminMeResponse> me(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		AdminUser admin = adminUserRepository.findById(principal.adminId()).orElseThrow();
		return ResponseEntity.ok(new AdminMeResponse(
				admin.getAdminId() != null ? admin.getAdminId() : 0L,
				admin.getUsername(),
				admin.getEmail(),
				admin.getFullName(),
				admin.getRole()));
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(
			HttpServletRequest request,
			HttpServletResponse response) {
		if (request.getCookies() != null) {
			Arrays.stream(request.getCookies())
					.filter(c -> AdminSessionFilter.ADMIN_COOKIE_NAME.equals(c.getName()))
					.map(Cookie::getValue)
					.findFirst()
					.ifPresent(adminSessionService::logout);
		}

		ResponseCookie clearCookie = ResponseCookie.from(AdminSessionFilter.ADMIN_COOKIE_NAME, "")
				.httpOnly(true)
				.path("/api/v1/admin")
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());

		return ResponseEntity.noContent().build();
	}

	@GetMapping("/metrics")
	public ResponseEntity<AdminMetricsResponse> getMetrics(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		return ResponseEntity.ok(adminApplicationService.getMetrics());
	}

	@GetMapping("/applicants")
	public ResponseEntity<PageResponse<ApplicantSummaryResponse>> listApplicants(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
			@RequestParam(value = "paymentStatus", required = false, defaultValue = "ALL") String paymentStatus,
			@RequestParam(value = "q", required = false) String search,
			@RequestParam(value = "sort", required = false, defaultValue = "createdAt,desc") String sort) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Sort parsedSort = parseSort(sort, "createdAt", "registrationSubmittedAt", "fullName", "paymentStatus", "approvalStatus");
		Pageable pageable = PageRequest.of(safePage, safeSize, parsedSort);

		return ResponseEntity.ok(adminApplicationService.listApplicants(pageable, status, paymentStatus, search));
	}

	@GetMapping("/applicants/{applicantId}")
	public ResponseEntity<ApplicantDetailResponse> getApplicantDetails(
			@PathVariable("applicantId") Long applicantId,
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		ApplicantDetailResponse details = adminApplicationService.getApplicantDetails(applicantId);
		return ResponseEntity.ok(details);
	}

	@PostMapping("/applicants/{applicantId}/approve")
	public ResponseEntity<AdminApprovalResponse> approve(
			@PathVariable("applicantId") Long applicantId,
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@RequestBody(required = false) AdminApprovalRequest request) {
		String notes = request != null ? request.notes() : null;
		AdminApprovalResponse response = adminApplicationService.approveApplicant(applicantId, principal.username(), notes);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/applicants/{applicantId}/reject")
	public ResponseEntity<AdminRejectionResponse> reject(
			@PathVariable("applicantId") Long applicantId,
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@Valid @RequestBody AdminRejectionRequest request) {
		AdminRejectionResponse response = adminApplicationService.rejectApplicant(applicantId, principal.username(), request.reason());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/applicants/{applicantId}/documents/{documentId}/content")
	public ResponseEntity<byte[]> getDocumentContent(
			@PathVariable("applicantId") Long applicantId,
			@PathVariable("documentId") String documentId,
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		ApplicantDocumentContent content = adminApplicationService.getDocumentContent(applicantId, documentId, principal.username());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(content.contentType()));
		headers.setContentDisposition(ContentDisposition.inline().filename(content.filename()).build());
		headers.setCacheControl(CacheControl.noStore());
		headers.set("X-Content-Type-Options", "nosniff");
		headers.set("Content-Security-Policy", "default-src 'none'");

		return ResponseEntity.ok().headers(headers).body(content.content());
	}

	@PostMapping("/applicants/{applicantId}/documents/{documentId}/review")
	public ResponseEntity<DocumentReviewResponse> reviewDocument(
			@PathVariable("applicantId") Long applicantId,
			@PathVariable("documentId") String documentId,
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@Valid @RequestBody DocumentReviewRequest request) {
		DocumentReviewResponse response = adminApplicationService.reviewDocument(
				applicantId,
				documentId,
				request.status(),
				request.rejectionReason(),
				principal.role());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/payments")
	public ResponseEntity<PageResponse<PaymentTransactionSummaryResponse>> listPayments(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
			@RequestParam(value = "sort", required = false, defaultValue = "createdAt,desc") String sort) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Sort parsedSort = parseSort(sort, "createdAt", "amountPaise", "status");
		Pageable pageable = PageRequest.of(safePage, safeSize, parsedSort);

		return ResponseEntity.ok(adminApplicationService.listPayments(pageable, status));
	}

	@GetMapping("/affiliates")
	public ResponseEntity<PageResponse<AffiliateSummaryResponse>> listAffiliates(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size,
			@RequestParam(value = "q", required = false) String search,
			@RequestParam(value = "sort", required = false, defaultValue = "activatedAt,desc") String sort) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Sort parsedSort = parseSort(sort, "activatedAt", "affiliateId", "status");
		Pageable pageable = PageRequest.of(safePage, safeSize, parsedSort);

		return ResponseEntity.ok(adminApplicationService.listAffiliates(pageable, search));
	}

	@GetMapping("/audit-logs")
	public ResponseEntity<PageResponse<ApprovalHistoryInfo>> listAuditLogs(
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, Math.min(size, 100));
		Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));

		return ResponseEntity.ok(adminApplicationService.listAuditLogs(pageable));
	}

	private String extractClientIp(HttpServletRequest request) {
		String clientIp = request.getHeader("X-Forwarded-For");
		if (clientIp != null && !clientIp.isBlank()) {
			return clientIp.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private Sort parseSort(String sortParam, String... allowedFields) {
		if (sortParam == null || sortParam.isBlank()) {
			return Sort.by(Sort.Direction.DESC, allowedFields.length > 0 ? allowedFields[0] : "createdAt");
		}
		String[] parts = sortParam.split(",");
		String field = parts[0].trim();
		Sort.Direction direction = Sort.Direction.DESC;
		if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
			direction = Sort.Direction.ASC;
		}
		boolean isAllowed = false;
		for (String allowed : allowedFields) {
			if (allowed.equalsIgnoreCase(field)) {
				field = allowed;
				isAllowed = true;
				break;
			}
		}
		if (!isAllowed) {
			field = allowedFields.length > 0 ? allowedFields[0] : "createdAt";
		}
		return Sort.by(direction, field);
	}
}
