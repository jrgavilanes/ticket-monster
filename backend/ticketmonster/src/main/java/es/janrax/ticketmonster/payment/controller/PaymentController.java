package es.janrax.ticketmonster.payment.controller;

import es.janrax.ticketmonster.payment.model.Payment;
import es.janrax.ticketmonster.payment.service.PaymentAccessDeniedException;
import es.janrax.ticketmonster.payment.service.PaymentNotFoundException;
import es.janrax.ticketmonster.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ResponseEntity<?> initiatePayment(
			@RequestBody InitiatePaymentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		Payment payment = paymentService.initiatePayment(userId, request.reservationId(), request.amount());
		return ResponseEntity.status(201).body(toResponse(payment));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getPayment(
			@PathVariable String id,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		try {
			Payment payment = paymentService.getPayment(id, userId);
			return ResponseEntity.ok(toResponse(payment));
		} catch (PaymentNotFoundException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		} catch (PaymentAccessDeniedException e) {
			return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/{id}/confirm")
	public ResponseEntity<?> confirmPayment(
			@PathVariable String id,
			@RequestBody ConfirmPaymentRequest request) {
		try {
			Payment payment = paymentService.confirmPayment(id, request.idempotencyKey());
			return ResponseEntity.ok(toResponse(payment));
		} catch (PaymentNotFoundException e) {
			return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
		}
	}

	private Map<String, Object> toResponse(Payment payment) {
		return Map.of(
				"id", payment.getId(),
				"reservationId", payment.getReservationId(),
				"userId", payment.getUserId(),
				"amount", payment.getAmount().toString(),
				"status", payment.getStatus().name(),
				"createdAt", payment.getCreatedAt().toString(),
				"confirmedAt", payment.getConfirmedAt() != null ? payment.getConfirmedAt().toString() : ""
		);
	}

	public record InitiatePaymentRequest(String reservationId, BigDecimal amount) {}
	public record ConfirmPaymentRequest(String idempotencyKey) {}
}
