package android.security.keystore

import java.security.spec.AlgorithmParameterSpec

/**
 * 单元测试环境下的虚假 KeyGenParameterSpec，用于规避 JVM 单元测试时 android.jar 的 'Method not mocked' 报错。
 */
class KeyGenParameterSpec private constructor(
    /**
     * 密钥别名。
     */
    val keystoreAlias: String,
    /**
     * 密钥用途。
     */
    val purposes: Int
) : AlgorithmParameterSpec {

    /**
     * 构建器。
     */
    class Builder(
        private val keystoreAlias: String,
        private val purposes: Int
    ) {
        /**
         * 设置分组模式。
         */
        fun setBlockModes(vararg blockModes: String): Builder = this

        /**
         * 设置填充模式。
         */
        fun setEncryptionPaddings(vararg paddings: String): Builder = this

        /**
         * 是否需要随机加密。
         */
        fun setRandomizedEncryptionRequired(required: Boolean): Builder = this

        /**
         * 构建实例。
         */
        fun build(): KeyGenParameterSpec = KeyGenParameterSpec(keystoreAlias, purposes)
    }
}
