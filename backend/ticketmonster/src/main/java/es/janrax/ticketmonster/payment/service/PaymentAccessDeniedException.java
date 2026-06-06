package es.janrax.ticketmonster.payment.service;

public class PaymentAccessDeniedException extends RuntimeException {
	public PaymentAccessDeniedException(String paymentId) {
		super("Access denied to payment: " + paymentId);
	}
}
