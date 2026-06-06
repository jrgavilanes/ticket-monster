package es.janrax.ticketmonster.catalog.model;

import java.math.BigDecimal;

public class Zone {

	private String id;
	private String name;
	private int capacity;
	private BigDecimal price;
	private String section;

	public Zone() {}

	public Zone(String id, String name, int capacity, BigDecimal price, String section) {
		this.id = id;
		this.name = name;
		this.capacity = capacity;
		this.price = price;
		this.section = section;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public int getCapacity() { return capacity; }
	public void setCapacity(int capacity) { this.capacity = capacity; }
	public BigDecimal getPrice() { return price; }
	public void setPrice(BigDecimal price) { this.price = price; }
	public String getSection() { return section; }
	public void setSection(String section) { this.section = section; }
}
