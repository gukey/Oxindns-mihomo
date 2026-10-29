package dev.netpanel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Settings(
    val dnsWeb: String = "http://192.168.1.1:9199",
    val dnsApi: String = "",
    val dnsUser: String = "",
    val dnsPassword: String = "",
    val proxyWeb: String = "",
    val controller: String = "",
    val secret: String = "",
    val proxyGroup: String = "",
    val theme: Int = 0,
    val startTab: Int = 0,
    val refreshSeconds: Int = 5,
    val swipe: Boolean = true,
    val localSetupChecked: Boolean = false
) {
    fun resetInterface() = copy(theme = 0, startTab = 0, refreshSeconds = 5, swipe = true)
    override fun toString() = "Settings(redacted)"
    fun validate(): String? {
        for (value in listOf(dnsWeb, dnsApi, proxyWeb, controller)) {
            if (value.isBlank()) continue
            val url = value.toHttpUrlOrNull() ?: return "地址必须是完整的 http:// 或 https:// 地址"
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
                return "地址不能包含账号、密码、查询参数或片段，请使用独立认证设置"
        }
        if (refreshSeconds !in 2..60) return "刷新间隔应为 2–60 秒"
        if (theme !in 0..2 || startTab !in 0..2) return "界面设置无效"
        if (listOf(secret, dnsUser, dnsPassword).any { '\r' in it || '\n' in it }) return "认证内容不能包含换行"
        if (':' in dnsUser) return "Basic Auth 用户名不能包含冒号"
        if (secret.any { it.code !in 32..126 }) return "Secret 需使用可用于 HTTP 请求头的 ASCII 字符"
        return null
    }
}

private val Context.settingsDataStore by preferencesDataStore("netpanel")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore
    private val field = stringPreferencesKey("encrypted_settings_v1")
    private val crypto = SettingsCrypto()
    val settings = dataStore.data.map { prefs ->
        prefs[field]?.let { decode(crypto.decrypt(it)) } ?: Settings()
    }.flowOn(Dispatchers.IO)
    suspend fun save(s: Settings) {
        require(s.validate() == null)
        val j = JSONObject().apply {
            put("dnsWeb", s.dnsWeb); put("dnsApi", s.dnsApi); put("dnsUser", s.dnsUser)
            put("dnsPassword", s.dnsPassword); put("proxyWeb", s.proxyWeb); put("controller", s.controller)
            put("secret", s.secret); put("proxyGroup", s.proxyGroup); put("theme", s.theme)
            put("startTab", s.startTab); put("refreshSeconds", s.refreshSeconds); put("swipe", s.swipe)
            put("localSetupChecked", s.localSetupChecked)
        }
        val encrypted = crypto.encrypt(j.toString())
        dataStore.edit { it[field] = encrypted }
    }
    suspend fun clear() { dataStore.edit { it.remove(field) } }
    private fun decode(raw: String): Settings {
        val j = JSONObject(raw)
        return Settings(j.optString("dnsWeb", "http://192.168.1.1:9199"), j.optString("dnsApi"),
            j.optString("dnsUser"), j.optString("dnsPassword"), j.optString("proxyWeb"),
            j.optString("controller"), j.optString("secret"), j.optString("proxyGroup"),
            j.optInt("theme"), j.optInt("startTab"), j.optInt("refreshSeconds", 5), j.optBoolean("swipe", true), j.optBoolean("localSetupChecked", false))
            .also { require(it.validate() == null) }
    }
}

private class SettingsCrypto {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("netpanel.settings.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("netpanel.settings.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(raw.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(raw: String): String {
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        require(bytes.size > 28)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
