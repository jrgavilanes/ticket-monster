package es.janrax.ticketmonster.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "es.janrax.ticketmonster.payment.repository")
public class PaymentConfig {
}
