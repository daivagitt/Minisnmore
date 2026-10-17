package com.miniandmore.shared.vault;

import org.apache.commons.lang3.StringUtils;

/** Env keys that must stay on the host (bootstrap / chicken-and-egg). */
public final class VaultBootstrapDenylist {

    private VaultBootstrapDenylist() {}

    public static boolean isBootstrapKey(String name) {
        if (StringUtils.isBlank(name)) {
            return true;
        }
        String trimmed = name.trim();
        return (
            "VAULT_MASTER_KEY".equals(trimmed) ||
            trimmed.startsWith("SPRING_DATASOURCE_") ||
            trimmed.startsWith("SPRING_R2DBC_") ||
            trimmed.startsWith("SPRING_LIQUIBASE_") ||
            trimmed.startsWith("VAULT_JDBC_")
        );
    }
}
