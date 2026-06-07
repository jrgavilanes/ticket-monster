package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.reservation.model.Reservation;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReservationEventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public ReservationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	public void publishReservationCreated(Reservation reservation) {
		kafkaTemplate.send("reservation-created", reservation.getId(), Map.of(
				"reservationId", reservation.getId(),
				"userId", reservation.getUserId(),
				"eventId", reservation.getEventId(),
				"status", reservation.getStatus().name()
		));
	}

	public void publishReservationCancelled(Reservation reservation) {
		kafkaTemplate.send("reservation-cancelled", reservation.getId(), Map.of(
				"reservationId", reservation.getId(),
				"userId", reservation.getUserId(),
				"eventId", reservation.getEventId()
		));
	}

	public void publishReservationExpired(Reservation reservation) {
		kafkaTemplate.send("reservation-expired", reservation.getId(), Map.of(
				"reservationId", reservation.getId(),
				"userId", reservation.getUserId(),
				"eventId", reservation.getEventId()
		));
	}

	public void publishPaymentRefundRequired(Reservation reservation) {
		kafkaTemplate.send("payment-refund-required", reservation.getId(), Map.of(
				"reservationId", reservation.getId(),
				"userId", reservation.getUserId(),
				"eventId", reservation.getEventId(),
				"reason", "reservation_expired"
		));
	}
}
