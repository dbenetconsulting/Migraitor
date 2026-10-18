package com.migration.config;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Encrypts passwords saved from the UI (AES-GCM). The key is "configkey" in the secrets file:
 * 32 random bytes in Base64, e.g. generated with PowerShell
 * [Convert]::ToBase64String((1..32 | % {Get-Random -Max 256}) -as [byte[]]).
 */
@Component
public class ConfigCrypto {

    private static final String PREFIX = "v1:";
    private final Secrets secrets;
    private final SecureRandom random = new SecureRandom();

    public ConfigCrypto(Secrets secrets) {
        this.secrets = secrets;
    }

    public boolean available() {
        return key().isPresent();
    }

    public String encrypt(String plain) {
        SecretKeySpec key = key().orElseThrow(() -> new IllegalStateException(
                "Add 'configkey' (32 bytes, Base64) to the secrets file before saving passwords from the UI"));
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Stored password has an unknown format");
        }
        SecretKeySpec key = key().orElseThrow(() -> new IllegalStateException("'configkey' missing in the secrets file"));
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot decrypt the stored password (was configkey changed?)", e);
        }
    }

    private Optional<SecretKeySpec> key() {
        return secrets.find("configkey").map(k -> {
            byte[] raw = Base64.getDecoder().decode(k);
            if (raw.length != 16 && raw.length != 32) {
                throw new IllegalStateException("configkey must be 16 or 32 bytes in Base64");
            }
            return new SecretKeySpec(raw, "AES");
        });
    }
}
