package es.janrax.ticketmonster.reservation.service;

public class ZoneNotFoundException extends RuntimeException {
	public ZoneNotFoundException(String eventId, String zoneId) {
		super("Zone not found: event=" + eventId + " zone=" + zoneId);
	}
}
