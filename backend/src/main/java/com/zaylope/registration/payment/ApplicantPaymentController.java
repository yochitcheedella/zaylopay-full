package com.zaylope.registration.payment;

import com.zaylope.registration.payment.PaymentDtos.PaymentOrderResponse;
import com.zaylope.registration.payment.PaymentDtos.PaymentStatusResponse;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationRequest;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationResponse;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me/payments")
public class ApplicantPaymentController {

	private final PaymentService paymentService;

	public ApplicantPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/create-order")
	public ResponseEntity<PaymentOrderResponse> createOrder(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		PaymentOrderResponse response = paymentService.createOrderForApplicant(principal.applicantId());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/verify")
	public ResponseEntity<PaymentVerificationResponse> verifyPayment(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody PaymentVerificationRequest request) {
		PaymentVerificationResponse response = paymentService.verifyPayment(principal.applicantId(), request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/status")
	public ResponseEntity<PaymentStatusResponse> getStatus(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		PaymentStatusResponse response = paymentService.getPaymentStatus(principal.applicantId());
		return ResponseEntity.ok(response);
	}
}
