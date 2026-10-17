package com.miniandmore.shared.vault;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;

/** AES-256-GCM for vault rows and encrypted columns ({@code v1:} + base64(iv || ciphertext || tag)). */
public final class VaultCrypto {

    public static final String ENCRYPTED_PREFIX = "v1:";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static volatile SecretKey masterKey;
    private static volatile boolean encryptionRequired;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private VaultCrypto() {}

    public static void initFromBase64MasterKey(String base64MasterKey) {
        if (StringUtils.isBlank(base64MasterKey)) {
            masterKey = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64MasterKey.trim());
        if (raw.length != 32) {
            throw new IllegalArgumentException("VAULT_MASTER_KEY must decode to 32 bytes (AES-256)");
        }
        masterKey = new SecretKeySpec(raw, "AES");
    }

    public static void setEncryptionRequired(boolean required) {
        encryptionRequired = required;
    }

    public static boolean isEncryptionRequired() {
        return encryptionRequired;
    }

    public static boolean isReady() {
        return masterKey != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.isEmpty()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        if (!isReady()) {
            if (encryptionRequired) {
                throw new IllegalStateException("VAULT_MASTER_KEY is required to encrypt secrets in production");
            }
            return plaintext;
        }
        SecretKey key = requireKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Vault encryption failed", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        SecretKey key = requireKey();
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length()));
            if (packed.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid vault ciphertext");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[packed.length - GCM_IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(packed, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Vault decryption failed", e);
        }
    }

    private static SecretKey requireKey() {
        SecretKey key = masterKey;
        if (key == null) {
            throw new IllegalStateException("VAULT_MASTER_KEY is not configured");
        }
        return key;
    }
}
