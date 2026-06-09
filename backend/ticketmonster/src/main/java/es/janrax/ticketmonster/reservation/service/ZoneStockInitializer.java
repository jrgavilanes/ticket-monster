package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.catalog.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ZoneStockInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ZoneStockInitializer.class);

	private final EventRepository eventRepository;
	private final ZoneStockSyncService zoneStockSyncService;

	public ZoneStockInitializer(EventRepository eventRepository, ZoneStockSyncService zoneStockSyncService) {
		this.eventRepository = eventRepository;
		this.zoneStockSyncService = zoneStockSyncService;
	}

	@Override
	public void run(ApplicationArguments args) {
		var events = eventRepository.findAll();
		for (var event : events) {
			if (event.getZones() != null && !event.getZones().isEmpty()) {
				var zones = event.getZones().stream()
						.map(z -> new ZoneStockSyncService.ZoneInfo(z.getId(), z.getCapacity()))
						.toList();
				zoneStockSyncService.createStock(event.getId(), zones);
			}
		}
		log.info("ZoneStock initialized for {} events", events.size());
	}
}
