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
