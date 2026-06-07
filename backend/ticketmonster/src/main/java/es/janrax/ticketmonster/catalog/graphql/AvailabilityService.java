package es.janrax.ticketmonster.catalog.graphql;

import java.util.List;

public interface AvailabilityService {

	List<ZoneAvailability> getAvailability(String eventId);

	record ZoneAvailability(String zoneId, String zoneName, int totalCapacity, int reservedCount, int availableCount) {}
}
