package es.janrax.ticketmonster.reservation.service;

import es.janrax.ticketmonster.catalog.event.ZonesModifiedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ZoneStockEventProcessor {

	private final ZoneStockSyncService zoneStockSyncService;

	public ZoneStockEventProcessor(ZoneStockSyncService zoneStockSyncService) {
		this.zoneStockSyncService = zoneStockSyncService;
	}

	@EventListener
	public void handleZonesModified(ZonesModifiedEvent event) {
		switch (event.action()) {
			case CREATED -> zoneStockSyncService.createStock(event.eventId(),
					event.zones().stream().map(z -> new ZoneStockSyncService.ZoneInfo(z.zoneId(), z.capacity())).toList());
			case UPDATED -> zoneStockSyncService.syncStock(event.eventId(),
					event.zones().stream().map(z -> new ZoneStockSyncService.ZoneInfo(z.zoneId(), z.capacity())).toList());
			case DELETED -> zoneStockSyncService.deleteStock(event.eventId());
		}
	}
}
