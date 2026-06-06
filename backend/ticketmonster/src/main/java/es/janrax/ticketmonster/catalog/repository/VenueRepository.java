package es.janrax.ticketmonster.catalog.repository;

import es.janrax.ticketmonster.catalog.model.Venue;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VenueRepository extends MongoRepository<Venue, String> {
}
