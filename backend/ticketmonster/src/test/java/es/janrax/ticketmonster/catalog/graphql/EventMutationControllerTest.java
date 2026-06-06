package es.janrax.ticketmonster.catalog.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class EventMutationControllerTest {

	@Test
	void controllerShouldHavePreAuthorizeAnnotation() {
		PreAuthorize annotation = EventMutationController.class.getAnnotation(PreAuthorize.class);
		assertThat(annotation).as("EventMutationController should be annotated with @PreAuthorize").isNotNull();
		assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
	}

	@Test
	void allMutationMethodsShouldBeProtected() throws Exception {
		for (Method method : EventMutationController.class.getDeclaredMethods()) {
			if (!method.getName().startsWith("create") && !method.getName().startsWith("update")
					&& !method.getName().startsWith("delete")) {
				continue;
			}
			PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
			assertThat(annotation)
				.as("Method %s should have @PreAuthorize (inherited from class)", method.getName())
				.isNull();
		}
	}
}
