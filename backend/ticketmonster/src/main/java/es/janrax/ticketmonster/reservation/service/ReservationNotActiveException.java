package es.janrax.ticketmonster.reservation.service;

public class ReservationNotActiveException extends RuntimeException {
	public ReservationNotActiveException(String reservationId) {
		super("Reservation is not active: " + reservationId);
	}
}
