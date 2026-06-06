package es.janrax.ticketmonster.reservation.service;

public class ReservationNotFoundException extends RuntimeException {
	public ReservationNotFoundException(String reservationId) {
		super("Reservation not found: " + reservationId);
	}
}
