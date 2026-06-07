package es.janrax.ticketmonster.payment.service;

public class PaymentNotFoundException extends RuntimeException {
	public PaymentNotFoundException(String paymentId) {
		super("Payment not found: " + paymentId);
	}
}
