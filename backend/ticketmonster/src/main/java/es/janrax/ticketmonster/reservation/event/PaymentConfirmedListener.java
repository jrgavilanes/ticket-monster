package es.janrax.ticketmonster.reservation.event;

import es.janrax.ticketmonster.reservation.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentConfirmedListener {

	private static final Logger log = LoggerFactory.getLogger(PaymentConfirmedListener.class);

	private final ReservationService reservationService;

	public PaymentConfirmedListener(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@KafkaListener(topics = "payment-confirmed", groupId = "ticketmonster-reservation")
	public void handlePaymentConfirmed(Map<String, Object> event) {
		String reservationId = (String) event.get("reservationId");
		log.info("Received payment-confirmed event for reservation {}", reservationId);
		try {
			reservationService.confirmSale(reservationId);
			log.info("Reservation {} converted to sale", reservationId);
		} catch (Exception e) {
			log.error("Failed to confirm sale for reservation {}: {}", reservationId, e.getMessage());
		}
	}
}
