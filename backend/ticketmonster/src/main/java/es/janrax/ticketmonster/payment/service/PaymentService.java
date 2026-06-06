package es.janrax.ticketmonster.payment.service;

import es.janrax.ticketmonster.payment.model.Payment;
import es.janrax.ticketmonster.payment.model.PaymentAudit;
import es.janrax.ticketmonster.payment.repository.PaymentAuditRepository;
import es.janrax.ticketmonster.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	private final PaymentRepository paymentRepository;
	private final PaymentAuditRepository auditRepository;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public PaymentService(PaymentRepository paymentRepository, PaymentAuditRepository auditRepository, KafkaTemplate<String, Object> kafkaTemplate) {
		this.paymentRepository = paymentRepository;
		this.auditRepository = auditRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@Transactional
	public Payment initiatePayment(String userId, String reservationId, BigDecimal amount) {
		Optional<Payment> existing = paymentRepository.findByReservationId(reservationId);
		if (existing.isPresent()) {
			return existing.get();
		}

		Payment payment = new Payment(reservationId, userId, amount);
		Payment saved = paymentRepository.save(payment);
		recordAudit(saved.getId(), "NONE", saved.getStatus().name(), userId);
		return saved;
	}

	@Transactional(readOnly = true)
	public Payment getPayment(String paymentId, String userId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException(paymentId));
		if (!payment.getUserId().equals(userId)) {
			throw new PaymentAccessDeniedException(paymentId);
		}
		return payment;
	}

	@Transactional
	public Payment confirmPayment(String paymentId, String idempotencyKey) {
		Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
		if (existingByKey.isPresent()) {
			return existingByKey.get();
		}

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException(paymentId));

		if (payment.getStatus() == Payment.PaymentStatus.CONFIRMED) {
			return payment;
		}

		String previousStatus = payment.getStatus().name();
		payment.setStatus(Payment.PaymentStatus.CONFIRMED);
		payment.setConfirmedAt(LocalDateTime.now());
		payment.setIdempotencyKey(idempotencyKey);
		Payment saved = paymentRepository.save(payment);

		recordAudit(saved.getId(), previousStatus, saved.getStatus().name(), "payment-gateway");

		kafkaTemplate.send("payment-confirmed", saved.getId(), Map.of(
				"paymentId", saved.getId(),
				"reservationId", saved.getReservationId(),
				"userId", saved.getUserId(),
				"amount", saved.getAmount().toString()
		));

		log.info("Payment {} confirmed for reservation {}", saved.getId(), saved.getReservationId());
		return saved;
	}

	private void recordAudit(String paymentId, String previousStatus, String newStatus, String actor) {
		auditRepository.save(new PaymentAudit(paymentId, previousStatus, newStatus, actor));
	}
}
