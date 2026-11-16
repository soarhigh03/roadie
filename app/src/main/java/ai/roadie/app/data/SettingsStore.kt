package ai.roadie.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import ai.roadie.core.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.roadieStore by preferencesDataStore("roadie_preferences")
class SettingsStore(private val context: Context) {
    private fun key(name: String) = booleanPreferencesKey(name)
    val preferences = context.roadieStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        Preferences(
            theme = runCatching { Theme.valueOf(p[stringPreferencesKey("theme")] ?: "DAY") }.getOrDefault(Theme.DAY),
            interests = p[stringSetPreferencesKey("interests")]?.mapNotNull { runCatching { Topic.valueOf(it) }.getOrNull() }?.toSet() ?: setOf(Topic.TECH, Topic.SCIENCE),
            proactive = p[key("proactive")] ?: false,
            remember = p[key("remember")] ?: false,
            restReminder = p[key("rest")] ?: false,
            resumeListening = p[key("resume")] ?: false,
            onboarded = p[key("onboarded")] ?: false,
            mode = runCatching { Mode.valueOf(p[stringPreferencesKey("mode")] ?: "LIVE") }.getOrDefault(Mode.LIVE),
        )
    }
    suspend fun save(value: Preferences) { context.roadieStore.edit {
        it[stringPreferencesKey("theme")] = value.theme.name
        it[stringSetPreferencesKey("interests")] = value.interests.map { t -> t.name }.toSet()
        it[key("proactive")] = value.proactive; it[key("remember")] = value.remember
        it[key("rest")] = value.restReminder; it[key("resume")] = value.resumeListening
        it[key("onboarded")] = value.onboarded; it[stringPreferencesKey("mode")] = value.mode.name
    } }
}
