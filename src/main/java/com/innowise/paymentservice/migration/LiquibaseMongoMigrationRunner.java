package com.innowise.paymentservice.migration;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.stereotype.Component;

/**
 * Runs the Liquibase MongoDB changelog on application startup. Spring Boot's own
 * {@code LiquibaseAutoConfiguration} only activates against a JDBC {@code DataSource}, which this
 * (Mongo-only) service does not have, so the {@code liquibase-mongodb} extension is driven
 * directly through the Liquibase Java API instead — the {@code mongodb://} URI resolves to
 * {@code MongoLiquibaseDatabase} via the extension's own {@code META-INF/services} registration,
 * with no extra wiring needed.
 *
 * <p>The URI comes from {@link MongoConnectionDetails}, not {@code @Value("${spring.data.mongodb.uri}")}
 * — Testcontainers' {@code @ServiceConnection} supplies the connection through this bean directly
 * and never populates the {@code spring.data.mongodb.uri} property, so a property-based lookup
 * resolves to nothing in any test using it (which is effectively all of them here).
 *
 * <p>Replaces {@code @EnableMongock} (Decision 5) — this is the only remaining piece that used to
 * be Mongock's job.
 */
@Component
@ConditionalOnProperty(name = "payment.migrations.enabled", havingValue = "true", matchIfMissing = true)
public class LiquibaseMongoMigrationRunner implements ApplicationRunner {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private final MongoConnectionDetails connectionDetails;

    public LiquibaseMongoMigrationRunner(MongoConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String mongoUri = connectionDetails.getConnectionString().getConnectionString();
        Database database = DatabaseFactory.getInstance()
                .openDatabase(mongoUri, null, null, null, new ClassLoaderResourceAccessor());
        try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
            liquibase.update();
        }
    }
}
