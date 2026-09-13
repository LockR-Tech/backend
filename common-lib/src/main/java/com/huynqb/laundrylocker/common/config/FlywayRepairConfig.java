package com.huynqb.laundrylocker.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Self-healing Flyway startup strategy shared by every service (picked up via the
 * services' {@code scanBasePackages = ...common}).
 *
 * <p>Background: services run Flyway on startup and validate migration checksums. If an
 * already-applied migration file is later edited (against the "add a new migration, never
 * change an applied one" rule), Flyway aborts with a checksum mismatch and the service
 * crash-loops. Because the deploy auto-applies migrations, a single edited migration can take
 * a whole service (e.g. user-service, which login/register depend on) offline in production.
 *
 * <p>This strategy runs {@code flyway.repair()} (realigns the schema-history checksums and
 * clears failed entries) before {@code flyway.migrate()} so such a mismatch self-heals instead
 * of blocking startup. The team convention is still to add new migrations rather than edit old
 * ones; this is only a safety net.
 *
 * <p>Disable with {@code app.flyway.repair-on-migrate=false} to restore strict validation.
 */
@Configuration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(name = "app.flyway.repair-on-migrate", havingValue = "true", matchIfMissing = true)
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrateStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
