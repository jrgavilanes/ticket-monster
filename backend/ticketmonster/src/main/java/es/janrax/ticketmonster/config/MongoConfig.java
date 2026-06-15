package es.janrax.ticketmonster.config;

import com.mongodb.MongoCredential;
import com.mongodb.lang.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConfig {

	@Value("${MONGO_USER:ticketmonster}")
	private String username;

	@Value("${MONGO_PASSWORD:ticketmonster}")
	private String password;

	@Value("${MONGO_AUTH_DB:admin}")
	private String authDb;

	@Bean
	public MongoClientSettingsBuilderCustomizer mongoCredentialCustomizer() {
		return new MongoClientSettingsBuilderCustomizer() {
			@Override
			public void customize(@NonNull com.mongodb.MongoClientSettings.Builder builder) {
				builder.credential(MongoCredential.createScramSha256Credential(
						username,
						authDb,
						password.toCharArray()
				));
			}
		};
	}
}
