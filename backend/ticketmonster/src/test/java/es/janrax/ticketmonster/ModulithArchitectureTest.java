package es.janrax.ticketmonster;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModulithArchitectureTest {

	ApplicationModules modules = ApplicationModules.of(TicketmonsterApplication.class);

	@Test
	void verifyModuleBoundaries() {
		modules.verify();
	}

	@Test
	void hasExpectedModules() {
		assertThat(modules.contains("catalog")).isTrue();
		assertThat(modules.contains("queue")).isTrue();
		assertThat(modules.contains("reservation")).isTrue();
		assertThat(modules.contains("payment")).isTrue();
	}
}
