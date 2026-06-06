package es.janrax.ticketmonster.catalog.repository;

import es.janrax.ticketmonster.catalog.model.Artist;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ArtistRepository extends MongoRepository<Artist, String> {
}
