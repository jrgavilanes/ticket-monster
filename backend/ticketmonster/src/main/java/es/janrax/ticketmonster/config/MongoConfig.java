package es.janrax.ticketmonster.config;

import com.mongodb.MongoCredential;
import com.mongodb.lang.NonNull;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

	@Bean
	public MongoClientSettingsBuilderCustomizer mongoCredentialCustomizer() {
		return new MongoClientSettingsBuilderCustomizer() {
			@Override
			public void customize(@NonNull com.mongodb.MongoClientSettings.Builder builder) {
				builder.credential(MongoCredential.createScramSha256Credential(
						"ticketmonster",
						"admin",
						"ticketmonster".toCharArray()
				));
			}
		};
	}
}
