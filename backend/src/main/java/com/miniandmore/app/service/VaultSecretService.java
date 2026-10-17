package com.miniandmore.app.service;

import com.miniandmore.app.domain.AppVaultSecret;
import com.miniandmore.app.domain.ZohoSetting;
import com.miniandmore.app.repository.AppVaultSecretRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VaultSecretService {

    private final AppVaultSecretRepository repository;
    private final ZohoSettingRepository zohoSettingRepository;

    public VaultSecretService(AppVaultSecretRepository repository, ZohoSettingRepository zohoSettingRepository) {
        this.repository = repository;
        this.zohoSettingRepository = zohoSettingRepository;
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
     * Decrypt all vault + Zoho secrets with the current {@code VAULT_MASTER_KEY}, then re-encrypt with {@code newBase64MasterKey}.
     * After this call, update {@code VAULT_MASTER_KEY} on every host and restart all services.
     */
    public Map<String, Object> rotateMasterKey(String newBase64MasterKey) {
        if (!VaultCrypto.isReady()) {
            throw new IllegalStateException("VAULT_MASTER_KEY must be configured before rotation");
        }
        if (StringUtils.isBlank(newBase64MasterKey)) {
            throw new IllegalArgumentException("newMasterKey is required");
        }
        String newKey = newBase64MasterKey.trim();
        String currentKey = System.getenv("VAULT_MASTER_KEY");
        if (StringUtils.isBlank(currentKey)) {
            throw new IllegalStateException(
                "Cannot rotate: current VAULT_MASTER_KEY is not available in the process environment"
            );
        }

        // Decrypt everything with the CURRENT key before switching process-wide crypto state.
        Map<String, String> vaultPlaintext = new LinkedHashMap<>();
        for (AppVaultSecret row : repository.findAll()) {
            vaultPlaintext.put(row.getName(), VaultCrypto.decrypt(row.getCiphertext()));
        }
        List<ZohoSetting> zohoPlaintext = new ArrayList<>();
        for (ZohoSetting setting : zohoSettingRepository.findAll()) {
            ZohoSetting copy = new ZohoSetting();
            copy.setId(setting.getId());
            copy.setClientSecret(setting.getClientSecret());
            copy.setRefreshToken(setting.getRefreshToken());
            zohoPlaintext.add(copy);
        }

        // Validate new key, then restore current key if validation fails.
        try {
            VaultCrypto.initFromBase64MasterKey(newKey);
            String probe = VaultCrypto.encrypt("rotation-probe");
            if (!VaultCrypto.isEncrypted(probe) || !"rotation-probe".equals(VaultCrypto.decrypt(probe))) {
                throw new IllegalStateException("New master key failed encrypt/decrypt probe");
            }
        } catch (RuntimeException e) {
            VaultCrypto.initFromBase64MasterKey(currentKey);
            throw new IllegalArgumentException("Invalid newMasterKey: " + e.getMessage(), e);
        }

        // Rewrite ciphertext under the new key. On failure, restore crypto state so the JVM stays usable.
        VaultCrypto.initFromBase64MasterKey(newKey);
        try {
            int vaultUpdated = 0;
            for (Map.Entry<String, String> entry : vaultPlaintext.entrySet()) {
                if (StringUtils.isBlank(entry.getValue())) {
                    continue;
                }
                putSecret(entry.getKey(), entry.getValue(), "master-key-rotation");
                vaultUpdated++;
            }
            int zohoUpdated = 0;
            for (ZohoSetting plain : zohoPlaintext) {
                ZohoSetting row = zohoSettingRepository.findById(plain.getId()).orElseThrow();
                row.setClientSecret(plain.getClientSecret());
                row.setRefreshToken(plain.getRefreshToken());
                zohoSettingRepository.save(row);
                zohoUpdated++;
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
        } catch (RuntimeException e) {
            VaultCrypto.initFromBase64MasterKey(currentKey);
            throw e;
        }
    }
}
