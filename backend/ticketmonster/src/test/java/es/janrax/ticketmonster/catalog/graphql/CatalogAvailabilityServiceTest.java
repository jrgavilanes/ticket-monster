package es.janrax.ticketmonster.catalog.graphql;

import es.janrax.ticketmonster.catalog.model.Event;
import es.janrax.ticketmonster.catalog.model.Zone;
import es.janrax.ticketmonster.catalog.repository.EventRepository;
import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.service.CatalogAvailabilityService;
import es.janrax.ticketmonster.reservation.service.ZoneStockSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAvailabilityServiceTest {

	@Mock
	private ZoneStockSyncService zoneStockSyncService;

	@Mock
	private EventRepository eventRepository;

	private CatalogAvailabilityService availabilityService;

	@BeforeEach
	void setUp() {
		availabilityService = new CatalogAvailabilityService(zoneStockSyncService, eventRepository);
	}

	@Test
	void getAvailability_shouldReturnZoneAvailabilities() {
		String eventId = "event-123";
		ZoneStock campo = new ZoneStock(eventId, "zone-campo", 50000);
		campo.setAvailableCount(48000);
		ZoneStock platea = new ZoneStock(eventId, "zone-platea", 20000);
		platea.setAvailableCount(15000);

		Event event = new Event();
		event.setZones(List.of(
				new Zone("zone-campo", "Campo", 50000, BigDecimal.valueOf(50), "GENERAL"),
				new Zone("zone-platea", "Platea", 20000, BigDecimal.valueOf(120), "VIP")
		));

		when(zoneStockSyncService.getStock(eventId)).thenReturn(List.of(campo, platea));
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

		List<AvailabilityService.ZoneAvailability> result = availabilityService.getAvailability(eventId);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).zoneId()).isEqualTo("zone-campo");
		assertThat(result.get(0).zoneName()).isEqualTo("Campo");
		assertThat(result.get(0).totalCapacity()).isEqualTo(50000);
		assertThat(result.get(0).availableCount()).isEqualTo(48000);
		assertThat(result.get(0).reservedCount()).isEqualTo(2000);
		assertThat(result.get(1).zoneId()).isEqualTo("zone-platea");
		assertThat(result.get(1).zoneName()).isEqualTo("Platea");
	}

	@Test
	void getAvailability_shouldReturnEmptyList_whenNoStock() {
		String eventId = "event-unknown";
		when(zoneStockSyncService.getStock(eventId)).thenReturn(List.of());

		List<AvailabilityService.ZoneAvailability> result = availabilityService.getAvailability(eventId);

		assertThat(result).isEmpty();
	}
}
