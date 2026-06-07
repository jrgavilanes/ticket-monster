package es.janrax.ticketmonster.reservation.service;

public class ReservationAccessDeniedException extends RuntimeException {
	public ReservationAccessDeniedException(String reservationId) {
		super("Access denied to reservation: " + reservationId);
	}
}
