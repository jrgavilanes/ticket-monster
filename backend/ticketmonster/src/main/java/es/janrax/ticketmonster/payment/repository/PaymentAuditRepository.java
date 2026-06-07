package es.janrax.ticketmonster.payment.repository;

import es.janrax.ticketmonster.payment.model.PaymentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, Long> {

	List<PaymentAudit> findByPaymentIdOrderByTimestampDesc(String paymentId);
}
