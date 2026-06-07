package es.janrax.ticketmonster.payment.repository;

import es.janrax.ticketmonster.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	Optional<Payment> findByReservationId(String reservationId);
}
