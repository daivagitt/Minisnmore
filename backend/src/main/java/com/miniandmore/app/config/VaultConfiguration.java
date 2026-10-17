package com.miniandmore.app.config;

import com.miniandmore.shared.vault.VaultCrypto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class VaultConfiguration {

    public VaultConfiguration(
        Environment environment,
        @Value("${VAULT_MASTER_KEY:}") String vaultMasterKey
    ) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        VaultCrypto.setEncryptionRequired(prod);
        if (StringUtils.isNotBlank(vaultMasterKey) && !VaultCrypto.isReady()) {
            VaultCrypto.initFromBase64MasterKey(vaultMasterKey);
        }
    }
}
