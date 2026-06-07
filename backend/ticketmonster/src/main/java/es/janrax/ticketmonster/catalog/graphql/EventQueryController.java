package es.janrax.ticketmonster.catalog.graphql;

import es.janrax.ticketmonster.catalog.model.Event;
import es.janrax.ticketmonster.catalog.model.Venue;
import es.janrax.ticketmonster.catalog.repository.ArtistRepository;
import es.janrax.ticketmonster.catalog.repository.EventRepository;
import es.janrax.ticketmonster.catalog.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class EventQueryController {

	private final EventRepository eventRepository;
	private final VenueRepository venueRepository;
	private final ArtistRepository artistRepository;

	public EventQueryController(EventRepository eventRepository, VenueRepository venueRepository, ArtistRepository artistRepository) {
		this.eventRepository = eventRepository;
		this.venueRepository = venueRepository;
		this.artistRepository = artistRepository;
	}

	@QueryMapping
	public EventPage events(@Argument int page, @Argument int size) {
		Page<Event> result = eventRepository.findByStatus(
				Event.EventStatus.PUBLISHED,
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "date"))
		);
		return EventPage.from(result);
	}

	@QueryMapping
	public Event event(@Argument String id) {
		return eventRepository.findById(id).orElse(null);
	}

	@QueryMapping
	public EventPage searchEvents(
			@Argument String query,
			@Argument Event.EventType type,
			@Argument String dateFrom,
			@Argument String dateTo,
			@Argument String venueId,
			@Argument int page,
			@Argument int size) {

		PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "date"));

		if (type != null) {
			return EventPage.from(eventRepository.findByStatusAndType(Event.EventStatus.PUBLISHED, type, pageRequest));
		}
		if (venueId != null) {
			return EventPage.from(eventRepository.findByStatusAndVenueId(Event.EventStatus.PUBLISHED, venueId, pageRequest));
		}
		if (dateFrom != null && dateTo != null) {
			return EventPage.from(eventRepository.findByStatusAndDateBetween(
					Event.EventStatus.PUBLISHED,
					LocalDateTime.parse(dateFrom),
					LocalDateTime.parse(dateTo),
					pageRequest
			));
		}

		return EventPage.from(eventRepository.searchByText(query, pageRequest));
	}

	@SchemaMapping(typeName = "Event", field = "venue")
	public Venue venue(Event event) {
		return venueRepository.findById(event.getVenueId()).orElse(null);
	}

	@SchemaMapping(typeName = "Event", field = "artists")
	public Object artists(Event event) {
		if (event.getArtistIds() == null || event.getArtistIds().isEmpty()) {
			return java.util.List.of();
		}
		return artistRepository.findAllById(event.getArtistIds());
	}
}
