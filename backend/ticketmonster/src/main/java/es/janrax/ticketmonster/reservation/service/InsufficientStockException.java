package es.janrax.ticketmonster.reservation.service;

public class InsufficientStockException extends RuntimeException {
	public InsufficientStockException(String eventId, String zoneId, int available, int requested) {
		super("Insufficient stock for event " + eventId + " zone " + zoneId + ": available=" + available + " requested=" + requested);
	}
}
