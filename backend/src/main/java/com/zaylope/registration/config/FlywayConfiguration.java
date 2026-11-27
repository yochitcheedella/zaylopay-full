package com.zaylope.registration.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlywayConfiguration.class);

	@Bean(name = "flyway")
	@Order(-1000)
	public Flyway flyway(
			DataSource dataSource,
			@Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
			@Value("${spring.flyway.baseline-version:9}") String baselineVersion,
			@Value("${spring.flyway.locations:classpath:db/migration}") String locations) {
		LOGGER.info("Initializing Flyway database migration: baselineOnMigrate={}, baselineVersion={}, locations={}",
				baselineOnMigrate, baselineVersion, locations);

		Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.baselineOnMigrate(baselineOnMigrate)
				.baselineVersion(baselineVersion)
				.locations(locations)
				.load();

		MigrateResult result = flyway.migrate();
		LOGGER.info("Flyway migration finished. Migrations executed: {}, target schema version: {}, success: {}",
				result.migrationsExecuted, result.targetSchemaVersion, result.success);

		return flyway;
	}
}
