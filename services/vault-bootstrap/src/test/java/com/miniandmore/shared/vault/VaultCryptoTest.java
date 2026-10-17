package com.miniandmore.shared.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultCryptoTest {

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        VaultCrypto.initFromBase64MasterKey(Base64.getEncoder().encodeToString(key));
        VaultCrypto.setEncryptionRequired(false);
    }

    @Test
    void encryptDecrypt_roundTrip() {
        String plain = "zoho-client-secret-value";
        String encrypted = VaultCrypto.encrypt(plain);
        assertThat(encrypted).startsWith(VaultCrypto.ENCRYPTED_PREFIX);
        assertThat(VaultCrypto.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void encrypt_whenRequiredAndNoKey_fails() {
        VaultCrypto.initFromBase64MasterKey("");
        VaultCrypto.setEncryptionRequired(true);
        assertThatThrownBy(() -> VaultCrypto.encrypt("secret")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptWith_doesNotMutateProcessKey() {
        byte[] other = new byte[32];
        for (int i = 0; i < other.length; i++) {
            other[i] = (byte) (100 + i);
        }
        String withOther = VaultCrypto.encryptWith(other, "scoped");
        assertThat(withOther).startsWith(VaultCrypto.ENCRYPTED_PREFIX);
        assertThat(VaultCrypto.decryptWith(other, withOther)).isEqualTo("scoped");
        // Process key still decrypts values encrypted with the @BeforeEach key.
        String withProcess = VaultCrypto.encrypt("still-process");
        assertThat(VaultCrypto.decrypt(withProcess)).isEqualTo("still-process");
        assertThatThrownBy(() -> VaultCrypto.decrypt(withOther)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validateMasterKey_rejectsBadLength() {
        assertThatThrownBy(() -> VaultCrypto.decodeMasterKey(Base64.getEncoder().encodeToString(new byte[16])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }
}
