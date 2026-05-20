package android.security.keystore

/**
 * 单元测试环境下的虚假 KeyProperties，用于规避 JVM 单元测试时 android.jar 的 'Method not mocked' 报错。
 */
object KeyProperties {
    /**
     * AES 算法。
     */
    const val KEY_ALGORITHM_AES = "AES"

    /**
     * 加密用途。
     */
    const val PURPOSE_ENCRYPT = 1

    /**
     * 解密用途。
     */
    const val PURPOSE_DECRYPT = 2

    /**
     * GCM 分组模式。
     */
    const val BLOCK_MODE_GCM = "GCM"

    /**
     * 无填充模式。
     */
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
}
