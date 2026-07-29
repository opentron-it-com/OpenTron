package org.opentron.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Embedded-profile Flyway strategy.
 *
 * Desktop users may carry a partially-applied migration metadata state from
 * previous builds. For the embedded H2 profile, repair first, then migrate.
 */
@Configuration
@Profile("embedded")
public class FlywayEmbeddedConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayEmbeddedConfig.class);

    @Bean
    public FlywayMigrationStrategy embeddedFlywayMigrationStrategy() {
        return flyway -> {
            logger.info("Running Flyway repair for embedded profile");
            flyway.repair();
            logger.info("Running Flyway migrate for embedded profile");
            flyway.migrate();
        };
    }
}
