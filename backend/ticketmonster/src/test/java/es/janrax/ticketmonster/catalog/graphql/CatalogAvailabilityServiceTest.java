package es.janrax.ticketmonster.catalog.graphql;

import es.janrax.ticketmonster.reservation.model.ZoneStock;
import es.janrax.ticketmonster.reservation.repository.ZoneStockRepository;
import es.janrax.ticketmonster.reservation.service.CatalogAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAvailabilityServiceTest {

	@Mock
	private ZoneStockRepository zoneStockRepository;

	@InjectMocks
	private CatalogAvailabilityService availabilityService;

	@Test
	void getAvailability_shouldReturnZoneAvailabilities() {
		String eventId = "event-123";
		ZoneStock campo = new ZoneStock(eventId, "zone-campo", 50000);
		campo.setAvailableCount(48000);
		ZoneStock platea = new ZoneStock(eventId, "zone-platea", 20000);
		platea.setAvailableCount(15000);

		when(zoneStockRepository.findByEventId(eventId)).thenReturn(List.of(campo, platea));

		List<AvailabilityService.ZoneAvailability> result = availabilityService.getAvailability(eventId);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).zoneId()).isEqualTo("zone-campo");
		assertThat(result.get(0).totalCapacity()).isEqualTo(50000);
		assertThat(result.get(0).availableCount()).isEqualTo(48000);
		assertThat(result.get(0).reservedCount()).isEqualTo(2000);
		assertThat(result.get(1).zoneId()).isEqualTo("zone-platea");
	}

	@Test
	void getAvailability_shouldReturnEmptyList_whenNoStock() {
		String eventId = "event-unknown";
		when(zoneStockRepository.findByEventId(eventId)).thenReturn(List.of());

		List<AvailabilityService.ZoneAvailability> result = availabilityService.getAvailability(eventId);

		assertThat(result).isEmpty();
	}
}
