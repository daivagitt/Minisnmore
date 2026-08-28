package com.miniandmore.app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/** Runs production database migrations without starting the HTTP application. */
public final class LiquibaseMigrationRunner {

    private static final String DEFAULT_CHANGELOG = "config/liquibase/master.xml";

    private LiquibaseMigrationRunner() {}

    public static void main(String[] args) throws Exception {
        String url = firstNonBlank(System.getenv("SPRING_LIQUIBASE_URL"), System.getenv("SPRING_DATASOURCE_URL"));
        String username = firstNonBlank(System.getenv("SPRING_LIQUIBASE_USERNAME"), System.getenv("SPRING_DATASOURCE_USERNAME"));
        String password = firstNonBlank(System.getenv("SPRING_LIQUIBASE_PASSWORD"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        String changelog = firstNonBlank(System.getenv("CATALOG_LIQUIBASE_CHANGELOG"), DEFAULT_CHANGELOG);

        requireSetting(url, "SPRING_LIQUIBASE_URL or SPRING_DATASOURCE_URL");
        requireSetting(username, "SPRING_LIQUIBASE_USERNAME or SPRING_DATASOURCE_USERNAME");
        requireSetting(password, "SPRING_LIQUIBASE_PASSWORD or SPRING_DATASOURCE_PASSWORD");
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("Migration URL must be a PostgreSQL JDBC URL");
        }

        Class.forName("org.postgresql.Driver");
        try (
            Connection connection = DriverManager.getConnection(url, username, password);
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor(Thread.currentThread().getContextClassLoader())
        ) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                Contexts contexts = new Contexts("prod");
                LabelExpression labels = new LabelExpression();
                liquibase.validate();
                int pending = liquibase.listUnrunChangeSets(contexts, labels).size();
                System.out.printf("Liquibase: %d production change set(s) pending%n", pending);
                liquibase.update(contexts, labels);
                System.out.println("Liquibase: database is up to date");
            }
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second == null ? null : second.trim();
    }

    private static void requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set before migrations run");
        }
    }
}
