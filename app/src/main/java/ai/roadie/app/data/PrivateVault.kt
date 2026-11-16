package ai.roadie.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ai.roadie.core.MemoryPort
import ai.roadie.core.Turn
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local AES-GCM storage. APK and backups contain neither tokens nor transcripts. */
class PrivateVault(context: Context) : MemoryPort {
    private val prefs = context.getSharedPreferences("private_vault", Context.MODE_PRIVATE)
    private val alias = "roadie.local.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(name: String): String = runCatching {
        val bytes = Base64.decode(prefs.getString(name, null) ?: return "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrDefault("")
    private fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "저장 공간을 확인해주세요." }
    }
    var endpoint: String
        get() = read("endpoint")
        set(value) = write("endpoint", value)
    var accessToken: String
        get() = read("access_token")
        set(value) = write("access_token", value)
    override fun load(): List<Turn> = runCatching {
        val array = JSONArray(read("memory").ifEmpty { "[]" })
        (0 until array.length()).map { array.getJSONObject(it) }.filter { it.optString("role") in setOf("user", "assistant") }
            .map { Turn(it.getString("role"), it.getString("text").take(1200)) }.takeLast(6)
    }.getOrDefault(emptyList())
    override fun save(turns: List<Turn>) = write("memory", JSONArray().apply {
        turns.takeLast(6).forEach { put(JSONObject().put("role", it.role).put("text", it.text.take(1200))) }
    }.toString())
    override fun clear() { check(prefs.edit().remove("memory").commit()) }
}
