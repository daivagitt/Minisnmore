package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.miniandmore.app.repository.AppVaultSecretRepository;
import com.miniandmore.app.repository.ZohoSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultSecretServiceTest {

    private VaultSecretService service;

    @BeforeEach
    void setUp() {
        service = new VaultSecretService(mock(AppVaultSecretRepository.class), mock(ZohoSettingRepository.class));
    }

    @Test
    void putSecret_rejectsBootstrapKey() {
        assertThatThrownBy(() -> service.putSecret("VAULT_MASTER_KEY", "x", "test"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bootstrap");
    }
}
