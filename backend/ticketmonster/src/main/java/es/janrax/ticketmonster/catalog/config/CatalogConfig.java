package es.janrax.ticketmonster.catalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "es.janrax.ticketmonster.catalog.repository")
@EnableMongoAuditing
public class CatalogConfig {
}
