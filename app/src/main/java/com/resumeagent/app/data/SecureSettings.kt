package com.resumeagent.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.resumeagent.app.domain.AiProviderId
import com.resumeagent.app.domain.AiProviderSettings
import com.resumeagent.app.domain.AiSettings
import com.resumeagent.app.domain.defaultAiProviders
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(private val context: Context) {
    companion object {
        private const val PREFS = "secure_settings"
        private const val LEGACY_API_KEY = "api_key"
        private const val API_KEY_PREFIX = "api_key_"
        private const val SETTINGS_JSON = "ai_settings"
        private const val KEY_ALIAS = "resume_agent_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val preferences
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readApiKey(provider: AiProviderId): String? = runCatching {
        val encoded = preferences.getString("$API_KEY_PREFIX${provider.name}", null)
            ?: if (provider == AiProviderId.ZHIPU) preferences.getString(LEGACY_API_KEY, null) else null
            ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, bytes.copyOfRange(0, 12))
        )
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun saveApiKey(provider: AiProviderId, value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(
                "$API_KEY_PREFIX${provider.name}",
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
            .remove(LEGACY_API_KEY)
            .apply()
    }

    fun clearApiKey(provider: AiProviderId) {
        val editor = preferences.edit().remove("$API_KEY_PREFIX${provider.name}")
        if (provider == AiProviderId.ZHIPU) editor.remove(LEGACY_API_KEY)
        editor.apply()
    }

    fun hasApiKey(provider: AiProviderId): Boolean = !readApiKey(provider).isNullOrBlank()

    fun readAiSettings(): AiSettings {
        val fallback = AiSettings(providers = defaultAiProviders())
        val raw = preferences.getString(SETTINGS_JSON, null) ?: return fallback
        return runCatching {
            val root = JSONObject(raw)
            val defaults = defaultAiProviders().associateBy { it.id }
            val providers = root.optJSONArray("providers").toProviders(defaults)
            AiSettings(
                providers = if (providers.isEmpty()) fallback.providers else providers,
                activeTextProvider = root.optString("activeTextProvider")
                    .toProviderIdOrFallback(fallback.activeTextProvider),
                activeVisionProvider = root.optString("activeVisionProvider")
                    .toProviderIdOrFallback(fallback.activeVisionProvider),
                dualReviewEnabled = root.optBoolean("dualReviewEnabled", false),
                activeReviewProvider = root.optString("activeReviewProvider")
                    .toProviderIdOrFallback(fallback.activeReviewProvider)
            )
        }.getOrDefault(fallback)
    }

    fun saveAiSettings(settings: AiSettings) {
        val root = JSONObject()
            .put("activeTextProvider", settings.activeTextProvider.name)
            .put("activeVisionProvider", settings.activeVisionProvider.name)
            .put("dualReviewEnabled", settings.dualReviewEnabled)
            .put("activeReviewProvider", settings.activeReviewProvider.name)
            .put("providers", JSONArray().apply {
                settings.providers.forEach { provider ->
                    put(JSONObject()
                        .put("id", provider.id.name)
                        .put("label", provider.label)
                        .put("endpoint", provider.endpoint.trim())
                        .put("modelsEndpoint", provider.modelsEndpoint.trim())
                        .put("textModel", provider.textModel.trim())
                        .put("visionModel", provider.visionModel.trim()))
                }
            })
        preferences.edit().putString(SETTINGS_JSON, root.toString()).apply()
    }

    private fun JSONArray?.toProviders(
        defaults: Map<AiProviderId, AiProviderSettings>
    ): List<AiProviderSettings> {
        if (this == null) return emptyList()
        val decoded = buildMap {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("id").toProviderIdOrNull() ?: continue
                val fallback = defaults[id] ?: continue
                put(
                    id,
                    AiProviderSettings(
                        id = id,
                        label = item.optString("label").ifBlank { fallback.label },
                        endpoint = item.optString("endpoint"),
                        modelsEndpoint = item.optString("modelsEndpoint")
                            .ifBlank { fallback.modelsEndpoint },
                        textModel = item.optString("textModel"),
                        visionModel = item.optString("visionModel")
                    )
                )
            }
        }
        return defaults.values.map { decoded[it.id] ?: it }
    }

    private fun String.toProviderIdOrNull(): AiProviderId? =
        runCatching { AiProviderId.valueOf(this) }.getOrNull()

    private fun String.toProviderIdOrFallback(fallback: AiProviderId): AiProviderId =
        toProviderIdOrNull() ?: fallback

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
