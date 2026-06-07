package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.reservation.model.Reservation;
import es.janrax.ticketmonster.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationExpirationSweeper {

	private static final Logger log = LoggerFactory.getLogger(ReservationExpirationSweeper.class);

	private final ReservationRepository reservationRepository;
	private final ReservationService reservationService;

	public ReservationExpirationSweeper(ReservationRepository reservationRepository, ReservationService reservationService) {
		this.reservationRepository = reservationRepository;
		this.reservationService = reservationService;
	}

	@Scheduled(fixedDelay = 60000)
	public void sweepExpiredReservations() {
		List<Reservation> expired = reservationRepository.findExpiredActiveReservations(LocalDateTime.now());
		if (!expired.isEmpty()) {
			log.info("Found {} expired reservations to process", expired.size());
		}
		for (Reservation reservation : expired) {
			try {
				reservationService.expireReservation(reservation);
			} catch (Exception e) {
				log.error("Failed to expire reservation {}: {}", reservation.getId(), e.getMessage());
			}
		}
	}
}
