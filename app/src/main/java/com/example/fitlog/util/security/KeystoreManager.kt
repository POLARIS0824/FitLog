package com.example.fitlog.util.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-GCM 加密管理器。
 *
 * 密钥由系统硬件安全模块生成并保护，应用进程无法导出明文密钥。
 * 即使设备被 root，只要 TEE/StrongBox 未被攻破，密文无法解密。
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "fitLog_api_key"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * 获取或创建 Keystore 密钥。
     *
     * @return [SecretKey]
     */
    private fun getOrCreateKey(): SecretKey {
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    /**
     * 加密明文。
     *
     * @param plainText 待加密的 API key 等敏感字符串
     * @return Base64 编码的密文（IV + ciphertext + tag）
     */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // IV (12 bytes) + ciphertext + auth tag (16 bytes)
        val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
            .put(iv)
            .put(cipherBytes)

        return android.util.Base64.encodeToString(buffer.array(), android.util.Base64.DEFAULT)
    }

    /**
     * 解密密文。
     *
     * 仅适用于密钥仍在 Keystore 中的正常场景；
     * 若密钥缺失（如备份恢复到新设备）或密文损坏，会抛出异常。
     * 需要容错的调用方应使用 [decryptOrNull]。
     *
     * @param encryptedBase64 Base64 编码的密文
     * @return 原始明文
     */
    fun decrypt(encryptedBase64: String): String {
        val encryptedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT)
        val buffer = ByteBuffer.wrap(encryptedBytes)

        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    /**
     * 容错解密密文：失败时返回 null 而非抛异常。
     *
     * 典型失败场景：
     * - 备份恢复到新设备后 Keystore 密钥不存在，[getOrCreateKey] 新建的密钥
     *   无法解开旧密文（GCM 认证失败）；
     * - 密文被截断/篡改，Base64 解码或 GCM tag 校验失败。
     *
     * 返回 null 表示“无法还原明文”，调用方（如 mapper）应降级处理
     * （apiKey 置空、配置仍可展示但不可用），保证不崩溃。
     * 明文 API Key 只存在于内存，失败时不会泄漏到日志或磁盘。
     *
     * @param encryptedBase64 Base64 编码的密文
     * @return 原始明文；密钥缺失或密文损坏时返回 null
     */
    fun decryptOrNull(encryptedBase64: String): String? {
        return try {
            decrypt(encryptedBase64)
        } catch (e: Exception) {
            // 解密失败统一降级为 null；
            // 这里无挂起点，捕获普通异常即可，不涉及协程取消传播。
            null
        }
    }
}
