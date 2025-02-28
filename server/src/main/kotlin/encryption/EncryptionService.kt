package com.github.spaceenthusiast.encryption

import com.github.spaceenthusiast.AppConfig
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class EncryptionService(
    appConfig: AppConfig
) {
    private val algorithm = "AES/CBC/PKCS5Padding"
    private val key = appConfig.encryptionKey
    private val iv = IvParameterSpec(ByteArray(16))

    fun encrypt(s: String): ByteArray {
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "AES")

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, iv)
        val encrypted = cipher.doFinal(s.toByteArray(charset("UTF-8")))

        return encrypted
    }

    fun decrypt(b: ByteArray): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "AES")

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv)
        val decrypted = cipher.doFinal(b)

        return String(decrypted, charset("UTF-8"))
    }
}