package ru.voiceactivation.demo.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM profile storage backed by an AndroidKeyStore key. */
class OwnerProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "voice_activation_secure_profile", Context.MODE_PRIVATE
    )

    fun save(modelFingerprint: String, embeddings: Array<FloatArray>) {
        require(modelFingerprint.isNotBlank() && embeddings.isNotEmpty())
        val dim = embeddings.first().size
        require(dim > 0 && embeddings.all { it.size == dim })
        val modelBytes = modelFingerprint.toByteArray(Charsets.UTF_8)
        val plain = ByteBuffer.allocate(12 + modelBytes.size + embeddings.size * dim * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(modelBytes.size)
            .put(modelBytes)
            .putInt(embeddings.size)
            .putInt(dim)
        embeddings.forEach { vector -> vector.forEach(plain::putFloat) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.array())
        val stored = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        check(prefs.edit().putString(KEY_PROFILE, Base64.encodeToString(stored, Base64.NO_WRAP)).commit())
    }

    fun load(expectedModelFingerprint: String): Array<FloatArray>? {
        val encoded = prefs.getString(KEY_PROFILE, null) ?: return null
        return try {
            val stored = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
            val ivSize = stored.int
            require(ivSize in 12..16)
            val iv = ByteArray(ivSize).also(stored::get)
            val encrypted = ByteArray(stored.remaining()).also(stored::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = ByteBuffer.wrap(cipher.doFinal(encrypted)).order(ByteOrder.LITTLE_ENDIAN)
            val fingerprintSize = plain.int
            require(fingerprintSize in 1..256 && plain.remaining() >= fingerprintSize + 8)
            val fingerprint = ByteArray(fingerprintSize).also(plain::get).toString(Charsets.UTF_8)
            if (fingerprint != expectedModelFingerprint) return null
            val count = plain.int
            val dim = plain.int
            require(count in 1..20 && dim in 1..4096 && plain.remaining() == count * dim * 4)
            Array(count) { FloatArray(dim) { plain.float } }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun hasProfile(): Boolean = prefs.contains(KEY_PROFILE)

    fun clear() {
        prefs.edit().remove(KEY_PROFILE).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voice_activation_owner_profile_v1"
        private const val KEY_PROFILE = "encrypted_embedding_profile"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
