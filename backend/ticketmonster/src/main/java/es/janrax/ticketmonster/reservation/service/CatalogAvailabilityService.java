package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.catalog.graphql.AvailabilityService;
import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.repository.ZoneStockRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogAvailabilityService implements AvailabilityService {

	private final ZoneStockRepository zoneStockRepository;

	public CatalogAvailabilityService(ZoneStockRepository zoneStockRepository) {
		this.zoneStockRepository = zoneStockRepository;
	}

	@Override
	public List<ZoneAvailability> getAvailability(String eventId) {
		return zoneStockRepository.findByEventId(eventId).stream()
				.map(stock -> new ZoneAvailability(
						stock.getZoneId(),
						stock.getZoneId(),
						stock.getTotalCapacity(),
						stock.getReservedCount(),
						stock.getAvailableCount()
				))
				.toList();
	}
}
