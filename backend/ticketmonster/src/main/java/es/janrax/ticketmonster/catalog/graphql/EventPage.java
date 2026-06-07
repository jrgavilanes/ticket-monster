package es.janrax.ticketmonster.catalog.graphql;

import es.janrax.ticketmonster.catalog.model.Event;
import org.springframework.data.domain.Page;

public record EventPage(
		java.util.List<Event> content,
		int totalElements,
		int totalPages,
		int page,
		int size
) {
	public static EventPage from(Page<Event> page) {
		return new EventPage(
				page.getContent(),
				(int) page.getTotalElements(),
				page.getTotalPages(),
				page.getNumber(),
				page.getSize()
		);
	}
}
