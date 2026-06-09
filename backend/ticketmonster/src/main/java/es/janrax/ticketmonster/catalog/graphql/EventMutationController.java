package es.janrax.ticketmonster.catalog.graphql;

import es.janrax.ticketmonster.catalog.event.ZonesModifiedEvent;
import es.janrax.ticketmonster.catalog.model.Artist;
import es.janrax.ticketmonster.catalog.model.Event;
import es.janrax.ticketmonster.catalog.model.Venue;
import es.janrax.ticketmonster.catalog.model.Zone;
import es.janrax.ticketmonster.catalog.repository.ArtistRepository;
import es.janrax.ticketmonster.catalog.repository.EventRepository;
import es.janrax.ticketmonster.catalog.repository.VenueRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class EventMutationController {

	private final EventRepository eventRepository;
	private final VenueRepository venueRepository;
	private final ArtistRepository artistRepository;
	private final ApplicationEventPublisher eventPublisher;

	public EventMutationController(EventRepository eventRepository, VenueRepository venueRepository, ArtistRepository artistRepository, ApplicationEventPublisher eventPublisher) {
		this.eventRepository = eventRepository;
		this.venueRepository = venueRepository;
		this.artistRepository = artistRepository;
		this.eventPublisher = eventPublisher;
	}

	@MutationMapping
	public Event createEvent(@Argument CreateEventInput input) {
		Event event = new Event(
				input.name(),
				input.description(),
				input.type(),
				LocalDateTime.parse(input.date()),
				input.venueId()
		);
		if (input.endDate() != null) {
			event.setEndDate(LocalDateTime.parse(input.endDate()));
		}
		if (input.artistIds() != null) {
			event.setArtistIds(input.artistIds());
		}
		List<Zone> zoneList = null;
		if (input.zones() != null) {
			zoneList = input.zones().stream()
					.map(z -> new Zone(
							z.id() != null ? z.id() : UUID.randomUUID().toString(),
							z.name(),
							z.capacity(),
							BigDecimal.valueOf(z.price()),
							z.section()
					))
					.toList();
			event.setZones(zoneList);
		}
		Event saved = eventRepository.save(event);
		if (zoneList != null) {
			eventPublisher.publishEvent(new ZonesModifiedEvent(
					saved.getId(),
					ZonesModifiedEvent.Action.CREATED,
					zoneList.stream().map(z -> new ZonesModifiedEvent.ZoneData(z.getId(), z.getName(), z.getCapacity())).toList()
			));
		}
		return saved;
	}

	@MutationMapping
	public Event updateEvent(@Argument String id, @Argument UpdateEventInput input) {
		Event event = eventRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));

		if (input.name() != null) event.setName(input.name());
		if (input.description() != null) event.setDescription(input.description());
		if (input.type() != null) event.setType(input.type());
		if (input.date() != null) event.setDate(LocalDateTime.parse(input.date()));
		if (input.endDate() != null) event.setEndDate(LocalDateTime.parse(input.endDate()));
		if (input.venueId() != null) event.setVenueId(input.venueId());
		if (input.artistIds() != null) event.setArtistIds(input.artistIds());
		if (input.status() != null) event.setStatus(input.status());
		List<Zone> updatedZones = null;
		if (input.zones() != null) {
			updatedZones = input.zones().stream()
					.map(z -> new Zone(
							z.id() != null ? z.id() : UUID.randomUUID().toString(),
							z.name(),
							z.capacity(),
							BigDecimal.valueOf(z.price()),
							z.section()
					))
					.toList();
			event.setZones(updatedZones);
		}
		event.setUpdatedAt(LocalDateTime.now());
		Event saved = eventRepository.save(event);
		if (updatedZones != null) {
			eventPublisher.publishEvent(new ZonesModifiedEvent(
					saved.getId(),
					ZonesModifiedEvent.Action.UPDATED,
					updatedZones.stream().map(z -> new ZonesModifiedEvent.ZoneData(z.getId(), z.getName(), z.getCapacity())).toList()
			));
		}
		return saved;
	}

	@MutationMapping
	public boolean deleteEvent(@Argument String id) {
		eventRepository.deleteById(id);
		eventPublisher.publishEvent(new ZonesModifiedEvent(id, ZonesModifiedEvent.Action.DELETED, List.of()));
		return true;
	}

	@MutationMapping
	public Venue createVenue(@Argument CreateVenueInput input) {
		Venue venue = new Venue(
				input.name(),
				input.description(),
				new Venue.Location(input.address(), input.city(), input.country(), input.latitude(), input.longitude()),
				input.totalCapacity(),
				input.layoutType()
		);
		return venueRepository.save(venue);
	}

	@MutationMapping
	public Artist createArtist(@Argument CreateArtistInput input) {
		Artist artist = new Artist(input.name(), input.genre(), input.bio(), input.imageUrl());
		return artistRepository.save(artist);
	}

	public record CreateEventInput(
			String name, String description, Event.EventType type,
			String date, String endDate, String venueId,
			List<String> artistIds, List<ZoneInput> zones
	) {}

	public record UpdateEventInput(
			String name, String description, Event.EventType type,
			String date, String endDate, String venueId,
			List<String> artistIds, List<ZoneInput> zones, Event.EventStatus status
	) {}

	public record ZoneInput(String id, String name, int capacity, double price, String section) {}

	public record CreateVenueInput(
			String name, String description, String address, String city,
			String country, double latitude, double longitude,
			int totalCapacity, String layoutType
	) {}

	public record CreateArtistInput(String name, String genre, String bio, String imageUrl) {}
}
