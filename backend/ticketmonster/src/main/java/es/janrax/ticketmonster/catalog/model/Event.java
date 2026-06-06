package es.janrax.ticketmonster.catalog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "events")
public class Event {

	@Id
	private String id;

	@TextIndexed(weight = 3)
	private String name;

	private String description;
	private EventType type;
	private LocalDateTime date;
	private LocalDateTime endDate;
	private String venueId;
	private List<String> artistIds = new ArrayList<>();
	private List<Zone> zones = new ArrayList<>();
	private EventStatus status = EventStatus.DRAFT;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public enum EventType {
		CONCERT, SPORTS, THEATER, FESTIVAL, CONFERENCE, OTHER
	}

	public enum EventStatus {
		DRAFT, PUBLISHED, CANCELLED, COMPLETED
	}

	public Event() {}

	public Event(String name, String description, EventType type, LocalDateTime date, String venueId) {
		this.name = name;
		this.description = description;
		this.type = type;
		this.date = date;
		this.venueId = venueId;
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public EventType getType() { return type; }
	public void setType(EventType type) { this.type = type; }
	public LocalDateTime getDate() { return date; }
	public void setDate(LocalDateTime date) { this.date = date; }
	public LocalDateTime getEndDate() { return endDate; }
	public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
	public String getVenueId() { return venueId; }
	public void setVenueId(String venueId) { this.venueId = venueId; }
	public List<String> getArtistIds() { return artistIds; }
	public void setArtistIds(List<String> artistIds) { this.artistIds = artistIds; }
	public List<Zone> getZones() { return zones; }
	public void setZones(List<Zone> zones) { this.zones = zones; }
	public EventStatus getStatus() { return status; }
	public void setStatus(EventStatus status) { this.status = status; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
