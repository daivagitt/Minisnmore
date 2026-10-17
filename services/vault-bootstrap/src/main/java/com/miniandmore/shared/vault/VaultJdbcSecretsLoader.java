package com.miniandmore.shared.vault;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/** Loads decrypted vault entries over JDBC (before Spring datasource is available). */
public final class VaultJdbcSecretsLoader {

    private static final Logger LOG = LoggerFactory.getLogger(VaultJdbcSecretsLoader.class);

    private static final String SELECT_ALL = "SELECT name, ciphertext FROM app_vault_secret";

    private VaultJdbcSecretsLoader() {}

    public static Map<String, Object> loadDecryptedProperties(Environment environment) {
        if (!VaultCrypto.isReady()) {
            return Map.of();
        }
        JdbcBootstrap jdbc = resolveJdbcBootstrap(environment);
        if (jdbc == null) {
            return Map.of();
        }
        Map<String, Object> props = new LinkedHashMap<>();
        try {
            Connection connection = DriverManager.getConnection(jdbc.url, jdbc.username, jdbc.password);
            try (connection) {
                if (!tableExists(connection)) {
                    return Map.of();
                }
                try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
                    ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String ciphertext = rs.getString("ciphertext");
                        if (StringUtils.isBlank(name) || StringUtils.isBlank(ciphertext)) {
                            continue;
                        }
                        props.put(name.trim(), VaultCrypto.decrypt(ciphertext));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load secrets from app_vault_secret", e);
        }
        return props;
    }

    public static boolean vaultTableHasRows(Environment environment) {
        return countVaultRows(environment, false) > 0;
    }

    /** Row count in {@code app_vault_secret}, or {@code 0} if the table is missing. */
    public static int countVaultRows(Environment environment) {
        return countVaultRows(environment, true);
    }

    private static int countVaultRows(Environment environment, boolean failOnConnectionError) {
        JdbcBootstrap jdbc = resolveJdbcBootstrap(environment);
        if (jdbc == null) {
            return 0;
        }
        try {
            Connection connection = DriverManager.getConnection(jdbc.url, jdbc.username, jdbc.password);
            try (connection) {
                if (!tableExists(connection)) {
                    return 0;
                }
                try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM app_vault_secret");
                    ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (Exception e) {
            if (failOnConnectionError && StringUtils.isNotBlank(environment.getProperty("VAULT_JDBC_URL"))) {
                throw new IllegalStateException("Could not reach catalog DB for app_vault_secret at VAULT_JDBC_URL", e);
            }
            LOG.warn("Could not count app_vault_secret rows: {}", e.toString());
            return 0;
        }
    }

    public static void assertProductionReady(Environment environment) {
        if (!isProd(environment)) {
            return;
        }
        if (resolveJdbcBootstrap(environment) == null) {
            return;
        }
        int rows = countVaultRows(environment, StringUtils.isNotBlank(environment.getProperty("VAULT_JDBC_URL")));
        if (rows <= 0) {
            return;
        }
        if (StringUtils.isBlank(environment.getProperty("VAULT_MASTER_KEY"))) {
            throw new IllegalStateException("VAULT_MASTER_KEY must be set when app_vault_secret contains rows");
        }
    }

    private static boolean isProd(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tableExists(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'app_vault_secret'"
        );
        ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    static JdbcBootstrap resolveJdbcBootstrap(Environment environment) {
        String url = firstNonBlank(
            environment.getProperty("VAULT_JDBC_URL"),
            environment.getProperty("SPRING_DATASOURCE_URL"),
            environment.getProperty("spring.datasource.url")
        );
        String username = firstNonBlank(
            environment.getProperty("VAULT_JDBC_USERNAME"),
            environment.getProperty("SPRING_DATASOURCE_USERNAME"),
            environment.getProperty("spring.datasource.username")
        );
        String password = firstNonBlank(
            environment.getProperty("VAULT_JDBC_PASSWORD"),
            environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
            environment.getProperty("spring.datasource.password")
        );
        if (StringUtils.isBlank(url) || StringUtils.isBlank(username)) {
            return null;
        }
        return new JdbcBootstrap(url, username, password != null ? password : "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    record JdbcBootstrap(String url, String username, String password) {}
}
