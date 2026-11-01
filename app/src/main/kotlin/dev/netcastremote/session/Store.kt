package dev.netcastremote.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.netcastremote.model.Persisted
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.netcastStore by preferencesDataStore("netcast")

class TvStore(private val context: Context) {
    private val key = stringPreferencesKey("json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun read(): Persisted {
        val raw = context.netcastStore.data.first()[key] ?: return Persisted()
        return runCatching { json.decodeFromString(Persisted.serializer(), raw) }.getOrDefault(Persisted())
    }

    suspend fun write(value: Persisted) {
        val raw = json.encodeToString(Persisted.serializer(), value)
        context.netcastStore.edit { it[key] = raw }
    }
}
