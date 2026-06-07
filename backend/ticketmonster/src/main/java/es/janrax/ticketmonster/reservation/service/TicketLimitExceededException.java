package es.janrax.ticketmonster.reservation.service;

public class TicketLimitExceededException extends RuntimeException {
	public TicketLimitExceededException(int max, int requested) {
		super("Ticket limit exceeded: max " + max + " per customer, requested " + requested);
	}
}
