package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.repository.ZoneStockRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ZoneStockSyncService {

	private final ZoneStockRepository zoneStockRepository;

	public ZoneStockSyncService(ZoneStockRepository zoneStockRepository) {
		this.zoneStockRepository = zoneStockRepository;
	}

	@Transactional
	public void createStock(String eventId, List<ZoneInfo> zones) {
		for (ZoneInfo zone : zones) {
			if (zoneStockRepository.findByEventIdAndZoneIdForUpdate(eventId, zone.zoneId()).isEmpty()) {
				ZoneStock stock = new ZoneStock(eventId, zone.zoneId(), zone.capacity());
				zoneStockRepository.save(stock);
			}
		}
	}

	@Transactional
	public void syncStock(String eventId, List<ZoneInfo> zones) {
		List<ZoneStock> existing = zoneStockRepository.findByEventId(eventId);
		for (ZoneInfo zone : zones) {
			boolean found = existing.stream().anyMatch(s -> s.getZoneId().equals(zone.zoneId()));
			if (!found) {
				ZoneStock stock = new ZoneStock(eventId, zone.zoneId(), zone.capacity());
				zoneStockRepository.save(stock);
			}
		}
	}

	@Transactional
	public void deleteStock(String eventId) {
		zoneStockRepository.deleteByEventId(eventId);
	}

	@Transactional(readOnly = true)
	public List<ZoneStock> getStock(String eventId) {
		return zoneStockRepository.findByEventId(eventId);
	}

	public record ZoneInfo(String zoneId, int capacity) {}
}
