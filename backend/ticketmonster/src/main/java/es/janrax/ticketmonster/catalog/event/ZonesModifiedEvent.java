package es.janrax.ticketmonster.catalog.event;

import java.util.List;

public record ZonesModifiedEvent(String eventId, Action action, List<ZoneData> zones) {

	public enum Action {
		CREATED, UPDATED, DELETED
	}

	public record ZoneData(String zoneId, String name, int capacity) {}
}
