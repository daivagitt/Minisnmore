package com.miniandmore.shared.vault;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/** Injects decrypted {@code app_vault_secret} rows after OS env (real env still wins). */
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
            throw new IllegalStateException(
                "VAULT_MASTER_KEY is set but VAULT_JDBC_URL or SPRING_DATASOURCE_URL (and username) is missing"
            );
        }
        if (StringUtils.isBlank(environment.getProperty("VAULT_JDBC_URL"))) {
            // Gateway/store/commerce often have a service-local SPRING_DATASOURCE_URL; without an explicit
            // catalog VAULT_JDBC_URL the loader can silently see an empty/missing app_vault_secret table.
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equals(profile) || "uat".equals(profile)) {
                    throw new IllegalStateException(
                        "VAULT_MASTER_KEY is set in " +
                        profile +
                        " but VAULT_JDBC_URL is missing — set it to the catalog JDBC URL so vault secrets load from the correct database"
                    );
                }
            }
        }
        int vaultRows = VaultJdbcSecretsLoader.countVaultRows(environment);
        Map<String, Object> vaultProps = VaultJdbcSecretsLoader.loadDecryptedProperties(environment);
        if (vaultRows > 0 && vaultProps.isEmpty()) {
            throw new IllegalStateException(
                "app_vault_secret has " + vaultRows + " row(s) but no secrets were loaded — check VAULT_JDBC_* points at the catalog database"
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
}
