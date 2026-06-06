package es.janrax.ticketmonster.catalog.repository;

import es.janrax.ticketmonster.catalog.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;

public interface EventRepository extends MongoRepository<Event, String> {

	Page<Event> findByStatus(Event.EventStatus status, Pageable pageable);

	@Query("{ '$text': { '$search': ?0 }, 'status': 'PUBLISHED' }")
	Page<Event> searchByText(String query, Pageable pageable);

	Page<Event> findByStatusAndType(Event.EventStatus status, Event.EventType type, Pageable pageable);

	Page<Event> findByStatusAndVenueId(Event.EventStatus status, String venueId, Pageable pageable);

	Page<Event> findByStatusAndDateBetween(Event.EventStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
