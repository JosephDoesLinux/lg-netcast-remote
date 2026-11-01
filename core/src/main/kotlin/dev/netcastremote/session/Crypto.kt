package dev.netcastremote.session

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    fun seal(passphrase: CharArray, plain: ByteArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plain)
        return salt + iv + body
    }

    fun open(passphrase: CharArray, sealed: ByteArray): ByteArray {
        require(sealed.size > 28) { "Backup is too short" }
        val salt = sealed.copyOfRange(0, 16)
        val iv = sealed.copyOfRange(16, 28)
        val body = sealed.copyOfRange(28, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, 120_000, 256)
        val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
