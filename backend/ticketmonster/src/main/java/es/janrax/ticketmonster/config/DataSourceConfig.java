package es.janrax.ticketmonster.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class DataSourceConfig {

	@Value("${spring.datasource.url}")
	private String url;

	@Value("${spring.datasource.username}")
	private String username;

	@Value("${spring.datasource.password}")
	private String password;

	@Value("${spring.datasource.hikari.maximum-pool-size:20}")
	private int maxPoolSize;

	@Value("${spring.datasource.hikari.minimum-idle:5}")
	private int minIdle;

	@Bean
	@Primary
	public DataSource dataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(username);
		config.setPassword(password);
		config.setMaximumPoolSize(maxPoolSize);
		config.setMinimumIdle(minIdle);
		config.setConnectionTimeout(30000);
		return new HikariDataSource(config);
	}

	@Bean
	@Primary
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(dataSource);
		emf.setPackagesToScan(
			"es.janrax.ticketmonster.reservation.model",
			"es.janrax.ticketmonster.payment.model"
		);
		emf.setPersistenceUnitName("ticketmonster");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

		Properties props = new Properties();
		props.setProperty("hibernate.physical_naming_strategy",
			"org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
		emf.setJpaProperties(props);

		return emf;
	}

	@Bean
	@Primary
	public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean(initMethod = "migrate")
	public Flyway reservationFlyway(DataSource dataSource) {
		return Flyway.configure()
			.dataSource(dataSource)
			.defaultSchema("reservation")
			.schemas("reservation")
			.locations("classpath:db/migration/reservation")
			.createSchemas(true)
			.load();
	}

	@Bean(initMethod = "migrate")
	public Flyway paymentFlyway(DataSource dataSource) {
		return Flyway.configure()
			.dataSource(dataSource)
			.defaultSchema("payment")
			.schemas("payment")
			.locations("classpath:db/migration/payment")
			.createSchemas(true)
			.load();
	}
}
