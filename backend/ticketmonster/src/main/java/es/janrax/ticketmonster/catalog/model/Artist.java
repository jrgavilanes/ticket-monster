package es.janrax.ticketmonster.catalog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "artists")
public class Artist {

	@Id
	private String id;

	@TextIndexed(weight = 3)
	private String name;

	private String genre;
	private String bio;
	private String imageUrl;

	public Artist() {}

	public Artist(String name, String genre, String bio, String imageUrl) {
		this.name = name;
		this.genre = genre;
		this.bio = bio;
		this.imageUrl = imageUrl;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getGenre() { return genre; }
	public void setGenre(String genre) { this.genre = genre; }
	public String getBio() { return bio; }
	public void setBio(String bio) { this.bio = bio; }
	public String getImageUrl() { return imageUrl; }
	public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
