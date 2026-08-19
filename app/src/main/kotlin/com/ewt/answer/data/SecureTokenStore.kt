package com.ewt.answer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录态安全存储 —— Android Keystore 加密（AES/GCM）+ SharedPreferences。
 *
 * - 不保存账号密码，仅加密保存 EWT token
 * - 密钥由系统级 AndroidKeyStore 托管，不落盘
 * - 密钥不可导出，应用被卸载后自动失效
 */
class SecureTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("ewt_secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "ewt_token_key"

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun save(token: String) {
        if (token.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val encB64 = prefs.getString("token", null) ?: return null
        val ivB64 = prefs.getString("iv", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(encB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // 密钥失效 / 数据损坏时视为未登录
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
