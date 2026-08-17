package com.glownote.mobile.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small Keystore-backed wrapper for the WebDAV password. */
class CredentialCipher {
    private val alias = "glownote-webdav-credentials"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray())
        val payload = ByteBuffer.allocate(4 + cipher.iv.size + ciphertext.size)
        payload.putInt(cipher.iv.size)
        payload.put(cipher.iv)
        payload.put(ciphertext)
        return "v1:" + Base64.encodeToString(payload.array(), Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        if (value.isBlank() || !value.startsWith("v1:")) return value
        return try {
            val bytes = Base64.decode(value.removePrefix("v1:"), Base64.NO_WRAP)
            val payload = ByteBuffer.wrap(bytes)
            val ivLength = payload.int
            val iv = ByteArray(ivLength).also(payload::get)
            val ciphertext = ByteArray(payload.remaining()).also(payload::get)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
