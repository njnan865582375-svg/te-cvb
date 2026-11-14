package com.dsh.lock;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加解密需要落盘的敏感串
 * （SSH 密码、SSH 私钥）。密文格式：
 *
 *   kv1:<base64(iv || ciphertext||tag)>
 *
 * 没有 Keystore 可用时（极小概率）退化成 pl1: 前缀的明文，并在
 * {@link #isAvailable()} 里对外如实反映，UI 会给出警告。
 */
final class SecretVault {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "te_cvb_vault_v1";
    private static final String PREFIX_SEALED = "kv1:";
    private static final String PREFIX_PLAIN = "pl1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private SecretVault() {
    }

    static boolean isAvailable() {
        return key() != null;
    }

    /** 明文 -> 可落盘字符串。null 输入返回空串。 */
    static String seal(String plain) {
        if (plain == null || plain.length() == 0) {
            return "";
        }
        SecretKey key = key();
        if (key == null) {
            return PREFIX_PLAIN + Base64.encodeToString(
                    plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX_SEALED + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception exception) {
            return PREFIX_PLAIN + Base64.encodeToString(
                    plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    /** 可落盘字符串 -> 明文；兼容 3.9 之前直接存明文的旧值。 */
    static String open(String stored) {
        if (stored == null || stored.length() == 0) {
            return "";
        }
        try {
            if (stored.startsWith(PREFIX_PLAIN)) {
                return new String(Base64.decode(stored.substring(PREFIX_PLAIN.length()),
                        Base64.NO_WRAP), StandardCharsets.UTF_8);
            }
            if (!stored.startsWith(PREFIX_SEALED)) {
                // 旧版本明文，等下一次 save() 时自动迁移成密文
                return stored;
            }
            byte[] raw = Base64.decode(stored.substring(PREFIX_SEALED.length()), Base64.NO_WRAP);
            if (raw.length <= IV_LENGTH) {
                return "";
            }
            SecretKey key = key();
            if (key == null) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            // 密钥失效 / 数据被换过：当作没有存过，绝不让它变成崩溃
            return "";
        }
    }

    /** 是否仍是旧版明文存储（用于一次性迁移提示）。 */
    static boolean isLegacyPlaintext(String stored) {
        return stored != null && stored.length() > 0
                && !stored.startsWith(PREFIX_SEALED)
                && !stored.startsWith(PREFIX_PLAIN);
    }

    private static SecretKey key() {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (store.containsAlias(ALIAS)) {
                java.security.Key existing = store.getKey(ALIAS, null);
                if (existing instanceof SecretKey) {
                    return (SecretKey) existing;
                }
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (Exception exception) {
            return null;
        }
    }
}
