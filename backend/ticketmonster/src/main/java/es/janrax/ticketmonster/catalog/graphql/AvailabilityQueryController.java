package es.janrax.ticketmonster.catalog.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class AvailabilityQueryController {

	private final AvailabilityService availabilityService;

	public AvailabilityQueryController(AvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	@QueryMapping
	public List<AvailabilityService.ZoneAvailability> availability(@Argument String eventId) {
		return availabilityService.getAvailability(eventId);
	}
}
