package es.janrax.ticketmonster.catalog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "venues")
public class Venue {

	@Id
	private String id;

	@TextIndexed(weight = 2)
	private String name;

	private String description;
	private Location location;
	private int totalCapacity;
	private String layoutType;

	public Venue() {}

	public Venue(String name, String description, Location location, int totalCapacity, String layoutType) {
		this.name = name;
		this.description = description;
		this.location = location;
		this.totalCapacity = totalCapacity;
		this.layoutType = layoutType;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public Location getLocation() { return location; }
	public void setLocation(Location location) { this.location = location; }
	public int getTotalCapacity() { return totalCapacity; }
	public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
	public String getLayoutType() { return layoutType; }
	public void setLayoutType(String layoutType) { this.layoutType = layoutType; }

	public record Location(String address, String city, String country, double latitude, double longitude) {}
}
