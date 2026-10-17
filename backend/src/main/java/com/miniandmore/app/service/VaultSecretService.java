package com.miniandmore.app.service;

import com.miniandmore.app.domain.AppVaultSecret;
import com.miniandmore.app.repository.AppVaultSecretRepository;
import com.miniandmore.app.security.vault.VaultKnownKeys;
import com.miniandmore.shared.vault.VaultBootstrapDenylist;
import com.miniandmore.shared.vault.VaultCrypto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class VaultSecretService {

    private final AppVaultSecretRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public VaultSecretService(AppVaultSecretRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminView() {
        Map<String, AppVaultSecret> byName = new LinkedHashMap<>();
        repository.findAll().forEach(row -> byName.put(row.getName(), row));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String known : VaultKnownKeys.ALL) {
            rows.add(toAdminRow(known, byName.get(known)));
        }
        byName
            .keySet()
            .stream()
            .filter(name -> !VaultKnownKeys.ALL.contains(name))
            .sorted()
            .forEach(name -> rows.add(toAdminRow(name, byName.get(name))));
        return rows;
    }

    public void putSecret(String name, String plaintext, String updatedBy) {
        validateName(name);
        if (StringUtils.isBlank(plaintext)) {
            throw new IllegalArgumentException("Secret value must not be blank");
        }
        AppVaultSecret row = repository.findById(name).orElseGet(AppVaultSecret::new);
        row.setName(name);
        row.setCiphertext(VaultCrypto.encrypt(plaintext.trim()));
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(updatedBy);
        repository.save(row);
    }

    public void deleteSecret(String name) {
        validateName(name);
        repository.deleteById(name);
    }

    public int importSecrets(Map<String, String> values, String updatedBy) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || StringUtils.isBlank(entry.getValue())) {
                continue;
            }
            putSecret(entry.getKey().trim(), entry.getValue(), updatedBy);
            count++;
        }
        return count;
    }

    private static Map<String, Object> toAdminRow(String name, AppVaultSecret row) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("hasValue", row != null && StringUtils.isNotBlank(row.getCiphertext()));
        dto.put("updatedAt", row != null ? row.getUpdatedAt() : null);
        return dto;
    }

    private static void validateName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Secret name is required");
        }
        if (VaultBootstrapDenylist.isBootstrapKey(name)) {
            throw new IllegalArgumentException("Bootstrap secret cannot be stored in vault: " + name.trim());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Instant> latestUpdatedAt() {
        return repository.findAll().stream().map(AppVaultSecret::getUpdatedAt).filter(java.util.Objects::nonNull).max(Instant::compareTo);
    }

    /**
     * Re-encrypt vault + Zoho secrets under a new master key without mutating process-wide crypto until
     * the transaction commits. Zoho columns are rewritten via JDBC so Hibernate dirty-checking cannot
     * skip the UPDATE.
     * <p>After success, set {@code VAULT_MASTER_KEY} on every host to the new key and restart all services.
     */
    public Map<String, Object> rotateMasterKey(String newBase64MasterKey) {
        if (!VaultCrypto.isReady()) {
            throw new IllegalStateException("VAULT_MASTER_KEY must be configured before rotation");
        }
        if (StringUtils.isBlank(newBase64MasterKey)) {
            throw new IllegalArgumentException("newMasterKey is required");
        }
        String newKey = newBase64MasterKey.trim();
        final byte[] newRaw;
        try {
            newRaw = VaultCrypto.decodeMasterKey(newKey);
            VaultCrypto.validateMasterKey(newRaw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid newMasterKey: " + e.getMessage(), e);
        }

        // Decrypt with the CURRENT process key (unchanged until afterCommit).
        Map<String, String> vaultPlaintext = new LinkedHashMap<>();
        for (AppVaultSecret row : repository.findAll()) {
            vaultPlaintext.put(row.getName(), VaultCrypto.decrypt(row.getCiphertext()));
        }
        List<Map<String, Object>> zohoRows = jdbcTemplate.queryForList(
            "SELECT id, client_secret, refresh_token FROM zoho_setting"
        );

        int vaultUpdated = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, String> entry : vaultPlaintext.entrySet()) {
            if (StringUtils.isBlank(entry.getValue())) {
                continue;
            }
            AppVaultSecret row = repository.findById(entry.getKey()).orElseGet(AppVaultSecret::new);
            row.setName(entry.getKey());
            row.setCiphertext(VaultCrypto.encryptWith(newRaw, entry.getValue()));
            row.setUpdatedAt(now);
            row.setUpdatedBy("master-key-rotation");
            repository.save(row);
            vaultUpdated++;
        }

        int zohoUpdated = 0;
        for (Map<String, Object> row : zohoRows) {
            Long id = ((Number) row.get("id")).longValue();
            String clientSecret = row.get("client_secret") != null ? row.get("client_secret").toString() : null;
            String refreshToken = row.get("refresh_token") != null ? row.get("refresh_token").toString() : null;
            String plainClient = VaultCrypto.decrypt(clientSecret);
            String plainRefresh = VaultCrypto.decrypt(refreshToken);
            String newClient = StringUtils.isBlank(plainClient) ? clientSecret : VaultCrypto.encryptWith(newRaw, plainClient);
            String newRefresh = StringUtils.isBlank(plainRefresh)
                ? refreshToken
                : VaultCrypto.encryptWith(newRaw, plainRefresh);
            jdbcTemplate.update(
                "UPDATE zoho_setting SET client_secret = ?, refresh_token = ? WHERE id = ?",
                newClient,
                newRefresh,
                id
            );
            zohoUpdated++;
        }

        // Only switch process-wide crypto after a successful commit (rollback leaves old key intact).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        VaultCrypto.initFromBase64MasterKey(newKey);
                    }
                }
            );
        } else {
            VaultCrypto.initFromBase64MasterKey(newKey);
        }

        return Map.of(
            "ok",
            true,
            "vaultSecretsReencrypted",
            vaultUpdated,
            "zohoRowsReencrypted",
            zohoUpdated,
            "nextStep",
            "Set VAULT_MASTER_KEY on all servers to the new key and restart gateway, store, commerce, and catalog"
        );
    }
}
