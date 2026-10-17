package com.miniandmore.app.config;

import com.miniandmore.shared.vault.VaultCrypto;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (or flag-gated) migration of plaintext Zoho secrets to ciphertext at rest (bypasses JPA for raw column reads).
 */
@Component
public class ZohoPlaintextSecretMigrationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ZohoPlaintextSecretMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean migrateEnabled;

    public ZohoPlaintextSecretMigrationRunner(JdbcTemplate jdbcTemplate, ApplicationProperties applicationProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.migrateEnabled = applicationProperties.getVault().isMigrateZohoPlaintextSecrets();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!migrateEnabled) {
            return;
        }
        if (!VaultCrypto.isReady()) {
            throw new IllegalStateException(
                "VAULT_MIGRATE_ZOHO_PLAINTEXT=true but VAULT_MASTER_KEY is not loaded — refusing to leave Zoho secrets as plaintext"
            );
        }
        var rows = jdbcTemplate.queryForList("SELECT id, client_secret, refresh_token FROM zoho_setting");
        int updated = 0;
        for (var row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String clientSecret = row.get("client_secret") != null ? row.get("client_secret").toString() : null;
            String refreshToken = row.get("refresh_token") != null ? row.get("refresh_token").toString() : null;
            String newClient = clientSecret;
            String newRefresh = refreshToken;
            boolean changed = false;
            if (StringUtils.isNotBlank(clientSecret) && !VaultCrypto.isEncrypted(clientSecret)) {
                newClient = requireCiphertext(VaultCrypto.encrypt(clientSecret), "client_secret");
                changed = true;
            }
            if (StringUtils.isNotBlank(refreshToken) && !VaultCrypto.isEncrypted(refreshToken)) {
                newRefresh = requireCiphertext(VaultCrypto.encrypt(refreshToken), "refresh_token");
                changed = true;
            }
            if (changed) {
                jdbcTemplate.update(
                    "UPDATE zoho_setting SET client_secret = ?, refresh_token = ? WHERE id = ?",
                    newClient,
                    newRefresh,
                    id
                );
                updated++;
            }
        }
        if (updated > 0) {
            LOG.info("Encrypted plaintext Zoho credentials for {} row(s)", updated);
        } else {
            LOG.warn(
                "VAULT_MIGRATE_ZOHO_PLAINTEXT is still true but no plaintext Zoho rows remain — set it to false in host env"
            );
        }
    }

    private static String requireCiphertext(String value, String field) {
        if (!VaultCrypto.isEncrypted(value)) {
            throw new IllegalStateException(
                "Vault encrypt did not produce ciphertext for zoho_setting." + field + " — is VAULT_MASTER_KEY set?"
            );
        }
        return value;
    }
}
