package com.miniandmore.shared.vault;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Injects decrypted {@code app_vault_secret} rows after OS env (real env still wins).
 * Always initializes crypto when {@code VAULT_MASTER_KEY} is set so JPA column encryption works
 * even if JDBC bootstrap for the shared vault table is unavailable.
 */
public class VaultEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String VAULT_PROPERTY_SOURCE_NAME = "appVault";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String masterKey = environment.getProperty("VAULT_MASTER_KEY");
        if (StringUtils.isBlank(masterKey)) {
            return;
        }
        VaultCrypto.initFromBase64MasterKey(masterKey);
        if (VaultJdbcSecretsLoader.resolveJdbcBootstrap(environment) == null) {
            // Key is ready for encrypted columns; skip shared vault table load.
            return;
        }
        if (
            StringUtils.isBlank(environment.getProperty("VAULT_JDBC_URL")) &&
            isProdOrUat(environment)
        ) {
            // Prefer an explicit catalog URL for gateway/store/commerce — datasource may be service-local.
            System.err.println(
                "WARN: VAULT_MASTER_KEY is set without VAULT_JDBC_URL; loading app_vault_secret via SPRING_DATASOURCE_URL / spring.datasource.url — ensure that points at the catalog DB"
            );
        }
        int vaultRows = VaultJdbcSecretsLoader.countVaultRows(environment);
        Map<String, Object> vaultProps = VaultJdbcSecretsLoader.loadDecryptedProperties(environment);
        if (vaultRows > 0 && vaultProps.isEmpty()) {
            throw new IllegalStateException(
                "app_vault_secret has " +
                vaultRows +
                " row(s) but no secrets were loaded — check VAULT_JDBC_* points at the catalog database"
            );
        }
        if (vaultProps.isEmpty()) {
            return;
        }
        PropertySource<?> vaultSource = new MapPropertySource(VAULT_PROPERTY_SOURCE_NAME, vaultProps);
        if (environment.getPropertySources().contains("systemEnvironment")) {
            environment.getPropertySources().addAfter("systemEnvironment", vaultSource);
        } else {
            environment.getPropertySources().addLast(vaultSource);
        }
    }

    private static boolean isProdOrUat(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile) || "uat".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
