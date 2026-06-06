package es.janrax.ticketmonster.config;

import es.janrax.ticketmonster.reservation.model.Reservation;
import es.janrax.ticketmonster.reservation.repository.ReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricsConfig {

	private final Counter reservationCreatedCounter;
	private final Counter reservationExpiredCounter;
	private final Counter reservationConfirmedCounter;
	private final Counter reservationCancelledCounter;

	public MetricsConfig(MeterRegistry registry, ReservationRepository reservationRepository) {
		reservationCreatedCounter = Counter.builder("reservations.created")
				.description("Total reservations created")
				.register(registry);

		reservationExpiredCounter = Counter.builder("reservations.expired")
				.description("Total reservations expired")
				.register(registry);

		reservationConfirmedCounter = Counter.builder("reservations.confirmed")
				.description("Total reservations confirmed (sold)")
				.register(registry);

		reservationCancelledCounter = Counter.builder("reservations.cancelled")
				.description("Total reservations cancelled")
				.register(registry);

		Gauge.builder("reservations.active", reservationRepository,
						repo -> repo.countByStatus(Reservation.ReservationStatus.ACTIVE))
				.description("Current active reservations")
				.register(registry);
	}

	public Counter getReservationCreatedCounter() { return reservationCreatedCounter; }
	public Counter getReservationExpiredCounter() { return reservationExpiredCounter; }
	public Counter getReservationConfirmedCounter() { return reservationConfirmedCounter; }
	public Counter getReservationCancelledCounter() { return reservationCancelledCounter; }
}
