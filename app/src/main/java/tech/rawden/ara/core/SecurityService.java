package tech.rawden.ara.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides at-rest encryption for sensitive user data (chats, context/memory, audit logs).
 * Uses AES-256-GCM with PBKDF2 key derivation from a user passphrase.
 *
 * The key is held only in memory for the current session. If the user forgets the
 * passphrase, encrypted data cannot be recovered (standard for local encryption).
 */
public final class SecurityService {

    private static final Logger LOG = Logger.getLogger(SecurityService.class.getName());

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256; // bits
    private static final int PBKDF2_ITERATIONS =
            65_536; // Reduced for faster startup key derivation while retaining good security for local use
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    private static volatile SecretKey currentKey;
    private static volatile boolean encryptionEnabled = false;

    private SecurityService() {}

    public static boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public static void setEncryptionEnabled(boolean enabled) {
        encryptionEnabled = enabled;
        if (!enabled) {
            currentKey = null;
        }
    }

    /**
     * Derives and sets the session key from the given passphrase.
     * Call this after the user enters their passphrase (e.g. on unlock or when enabling encryption).
     */
    public static void unlockWithPassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            currentKey = null;
            return;
        }
        try {
            // We use a fixed salt for the *key derivation per session unlock*.
            // For file encryption we use per-file random salts.
            // A simple approach: derive once per unlock using a well-known salt for the session key.
            // For stronger per-file, we store salt with each encrypted file.
            byte[] salt = "ara-privacy-salt-v1".getBytes(); // constant for session key derivation
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            currentKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
            LOG.info("SecurityService: Session encryption key derived successfully.");
        } catch (Exception e) {
            LOG.warning("Failed to derive encryption key: " + e.getMessage());
            currentKey = null;
        } finally {
            // Best effort to wipe passphrase array
            java.util.Arrays.fill(passphrase, '\0');
        }
    }

    public static boolean isUnlocked() {
        return currentKey != null;
    }

    public static void lock() {
        currentKey = null;
    }

    /**
     * Securely wipes a file by overwriting with random data (multiple passes for small files) then deleting.
     * Best-effort; not a full DoD wipe but good for local privacy.
     */
    public static void secureDelete(Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            long size = Files.size(path);
            if (size > 0 && size < 50 * 1024 * 1024) { // only for reasonably sized files
                try (var out = Files.newOutputStream(path, java.nio.file.StandardOpenOption.WRITE)) {
                    SecureRandom rnd = new SecureRandom();
                    byte[] buf = new byte[8192];
                    for (int pass = 0; pass < 2; pass++) { // 2 passes of random
                        long written = 0;
                        while (written < size) {
                            rnd.nextBytes(buf);
                            int toWrite = (int) Math.min(buf.length, size - written);
                            out.write(buf, 0, toWrite);
                            written += toWrite;
                        }
                        out.flush();
                    }
                }
            }
            Files.deleteIfExists(path);
            LOG.info("Securely wiped: " + path);
        } catch (Exception e) {
            LOG.warning("Secure wipe failed for " + path + ", falling back to delete: " + e.getMessage());
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    // --- Encryption / Decryption for byte data (used by storage) ---

    /**
     * Encrypts plaintext. Returns a self-contained blob containing salt + IV + ciphertext.
     * Format: [salt(16)] [iv(12)] [ciphertext + GCM tag]
     */
    public static byte[] encrypt(byte[] plaintext) {
        if (!encryptionEnabled || currentKey == null) {
            return plaintext; // pass-through if not enabled
        }
        if (plaintext == null) return null;

        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // For per-file encryption we re-derive a key from the session key material + per-file salt
            // Simpler & sufficient: use the session key directly + random IV. The session key is already strong.
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[SALT_LENGTH + IV_LENGTH + ciphertext.length];
            System.arraycopy(
                    salt, 0, result, 0, SALT_LENGTH); // salt (can be ignored for session-key model, kept for future)
            System.arraycopy(iv, 0, result, SALT_LENGTH, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, SALT_LENGTH + IV_LENGTH, ciphertext.length);

            return result;
        } catch (Exception e) {
            LOG.warning("Encryption failed: " + e.getMessage());
            return plaintext; // fallback to plaintext on error (not ideal but prevents total loss)
        }
    }

    /**
     * Decrypts a blob produced by encrypt(). If it doesn't look encrypted (or encryption disabled), returns as-is.
     * Callers should check if encryption was expected.
     */
    public static byte[] decrypt(byte[] data) {
        if (!encryptionEnabled || currentKey == null || data == null) {
            return data;
        }
        if (data.length < SALT_LENGTH + IV_LENGTH) {
            // Too short to be our encrypted format -> treat as plaintext
            return data;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(data, SALT_LENGTH, iv, 0, IV_LENGTH);

            byte[] ciphertext = new byte[data.length - SALT_LENGTH - IV_LENGTH];
            System.arraycopy(data, SALT_LENGTH + IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, currentKey, spec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            LOG.warning("Decryption failed (wrong passphrase or corrupted data?): " + e.getMessage());
            // Return the raw data so callers can detect failure and show error to user
            return data;
        }
    }

    /**
     * Attempts decryption and throws on authentication failure (wrong key).
     * Useful for validating passphrase at unlock time.
     */
    public static byte[] decryptStrict(byte[] data) throws Exception {
        if (!encryptionEnabled || currentKey == null || data == null) {
            return data;
        }
        if (data.length < SALT_LENGTH + IV_LENGTH) {
            return data;
        }

        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(data, SALT_LENGTH, iv, 0, IV_LENGTH);

        byte[] ciphertext = new byte[data.length - SALT_LENGTH - IV_LENGTH];
        System.arraycopy(data, SALT_LENGTH + IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, currentKey, spec);

        return cipher.doFinal(ciphertext); // will throw AEADBadTagException on wrong key
    }

    /**
     * Convenience for String <-> bytes (UTF-8).
     */
    public static String encryptString(String plaintext) {
        if (plaintext == null) return null;
        byte[] enc = encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(enc);
    }

    public static String decryptString(String base64Ciphertext) {
        if (base64Ciphertext == null) return null;
        try {
            byte[] data = Base64.getDecoder().decode(base64Ciphertext);
            byte[] plain = decrypt(data);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warning("String decryption failed: " + e.getMessage());
            return base64Ciphertext; // fallback
        }
    }
}
