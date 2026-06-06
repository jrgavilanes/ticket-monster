package es.janrax.ticketmonster.reservation.service;

public class LockAcquisitionFailedException extends RuntimeException {
	public LockAcquisitionFailedException(String eventId, String zoneId) {
		super("Failed to acquire lock for event " + eventId + " zone " + zoneId);
	}
}
