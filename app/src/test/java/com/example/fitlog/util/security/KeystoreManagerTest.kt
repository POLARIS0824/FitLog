package com.example.fitlog.util.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.HashMap
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * 虚假的 AndroidKeyStore 安全提供者，用于在 JVM 单元测试环境中模拟 Android 的硬件 KeyStore。
 */
class FakeAndroidKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "Fake AndroidKeyStore provider") {
    init {
        put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", FakeKeyGeneratorSpi::class.java.name)
    }

    companion object {
        /**
         * 存储在内存中的 KeyStore 条目字典。
         */
        val entries = HashMap<String, KeyStore.Entry>()

        /**
         * 注册 FakeAndroidKeyStoreProvider 到 Java 的 Security 提供者列表中。
         */
        fun setup() {
            if (Security.getProvider("AndroidKeyStore") == null) {
                Security.addProvider(FakeAndroidKeyStoreProvider())
            }
        }
    }
}

/**
 * 模拟的 KeyStoreSpi 实现，支持内存中存储 and 检索 SecretKeyEntry。
 */
class FakeKeyStoreSpi : KeyStoreSpi() {
    override fun engineGetKey(alias: String?, password: CharArray?): Key? {
        return (FakeAndroidKeyStoreProvider.entries[alias] as? KeyStore.SecretKeyEntry)?.secretKey
    }

    override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String?): Certificate? = null
    override fun engineGetCreationDate(alias: String?): Date? = null
    override fun engineSetKeyEntry(alias: String?, key: Key?, password: CharArray?, chain: Array<out Certificate>?) {}
    override fun engineSetKeyEntry(alias: String?, keyBytes: ByteArray?, chain: Array<out Certificate>?) {}
    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) {}

    override fun engineSetEntry(alias: String?, entry: KeyStore.Entry?, protParam: KeyStore.ProtectionParameter?) {
        if (alias != null && entry != null) {
            FakeAndroidKeyStoreProvider.entries[alias] = entry
        }
    }

    override fun engineGetEntry(alias: String?, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? {
        return FakeAndroidKeyStoreProvider.entries[alias]
    }

    override fun engineDeleteEntry(alias: String?) {
        if (alias != null) {
            FakeAndroidKeyStoreProvider.entries.remove(alias)
        }
    }

    override fun engineAliases(): Enumeration<String> = Collections.enumeration(FakeAndroidKeyStoreProvider.entries.keys)
    override fun engineContainsAlias(alias: String?): Boolean = FakeAndroidKeyStoreProvider.entries.containsKey(alias)
    override fun engineSize(): Int = FakeAndroidKeyStoreProvider.entries.size
    override fun engineIsKeyEntry(alias: String?): Boolean = FakeAndroidKeyStoreProvider.entries[alias] is KeyStore.SecretKeyEntry
    override fun engineIsCertificateEntry(alias: String?): Boolean = false
    override fun engineGetCertificateAlias(cert: Certificate?): String? = null
    override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) {}
    override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) {}
}

/**
 * 模拟的 KeyGeneratorSpi 实现，生成 AES 密钥并在生成时自动将其保存到 FakeAndroidKeyStoreProvider。
 */
class FakeKeyGeneratorSpi : KeyGeneratorSpi() {
    private var alias: String = "fitLog_api_key"

    override fun engineInit(secureRandom: java.security.SecureRandom?) {}
    override fun engineInit(keysize: Int, secureRandom: java.security.SecureRandom?) {}

    override fun engineInit(params: java.security.spec.AlgorithmParameterSpec?, secureRandom: java.security.SecureRandom?) {
        if (params is android.security.keystore.KeyGenParameterSpec) {
            alias = params.keystoreAlias
        }
    }

    override fun engineGenerateKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        val key = kg.generateKey()
        FakeAndroidKeyStoreProvider.entries[alias] = KeyStore.SecretKeyEntry(key)
        return key
    }
}

/**
 * [KeystoreManager] 的加密与解密单元测试。
 * 使用 Robolectric 在 JVM 环境下虚拟化 Android 的 Keystore 加密服务和 Base64 解编码逻辑。
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreManagerTest {

    /**
     * 在运行每个测试用例前注册 FakeAndroidKeyStoreProvider。
     */
    @Before
    fun setUp() {
        FakeAndroidKeyStoreProvider.setup()
    }

    /**
     * 测试明文字符串加密后再解密，验证对称加解密是否完整一致。
     */
    @Test
    fun testEncryptionAndDecryptionSymmetry() {
        val originalText = "my-super-secret-api-key-12345"

        // 1. 加密
        val encryptedBase64 = KeystoreManager.encrypt(originalText)
        assertNotNull(encryptedBase64)
        assertNotEquals(originalText, encryptedBase64)

        // 2. 解密
        val decryptedText = KeystoreManager.decrypt(encryptedBase64)
        assertEquals(originalText, decryptedText)
    }

    /**
     * 测试多条不同敏感数据加密的独立性。
     */
    @Test
    fun testMultipleEncryptionsAreDistinct() {
        val originalText = "key-value"

        val firstEncryption = KeystoreManager.encrypt(originalText)
        val secondEncryption = KeystoreManager.encrypt(originalText)

        // 由于 AES-GCM 的随机 IV 要求，即使明文相同，两次生成的密文 Base64 字符串也应当不一致
        assertNotEquals(firstEncryption, secondEncryption)

        // 且两个不同密文都能正确解密回同一明文
        assertEquals(originalText, KeystoreManager.decrypt(firstEncryption))
        assertEquals(originalText, KeystoreManager.decrypt(secondEncryption))
    }
}
