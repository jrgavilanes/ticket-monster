package es.janrax.ticketmonster.reservation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableJpaRepositories(basePackages = "es.janrax.ticketmonster.reservation.repository")
@EnableScheduling
public class ReservationConfig {
}
