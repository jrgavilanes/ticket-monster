package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.catalog.graphql.AvailabilityService;
import es.janrax.ticketmonster.catalog.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogAvailabilityService implements AvailabilityService {

	private final ZoneStockSyncService zoneStockSyncService;
	private final EventRepository eventRepository;

	public CatalogAvailabilityService(ZoneStockSyncService zoneStockSyncService, EventRepository eventRepository) {
		this.zoneStockSyncService = zoneStockSyncService;
		this.eventRepository = eventRepository;
	}

	@Override
	public List<ZoneAvailability> getAvailability(String eventId) {
		return zoneStockSyncService.getStock(eventId).stream()
				.map(stock -> {
					String zoneName = eventRepository.findById(eventId)
							.map(event -> event.getZones().stream()
									.filter(z -> z.getId().equals(stock.getZoneId()))
									.findFirst()
									.map(z -> z.getName())
									.orElse(stock.getZoneId()))
							.orElse(stock.getZoneId());
					return new ZoneAvailability(
							stock.getZoneId(),
							zoneName,
							stock.getTotalCapacity(),
							stock.getReservedCount(),
							stock.getAvailableCount()
					);
				})
				.toList();
	}
}
