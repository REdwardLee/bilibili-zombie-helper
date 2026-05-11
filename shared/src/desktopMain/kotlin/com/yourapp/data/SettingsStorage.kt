package com.yourapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DesktopSettingsStorage : SettingsStorage {
    private val file = File(System.getProperty("user.home"), ".yourapp/settings.json")
    private val json = Json { prettyPrint = true }
    private var cache: MutableMap<String, String> = mutableMapOf()

    init {
        load()
    }

    private fun load() {
        if (file.exists()) {
            try {
                val content = file.readText()
                cache = json.decodeFromString(content)
            } catch (_: Exception) {
                cache = mutableMapOf()
            }
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(cache))
    }

    override suspend fun getString(key: String, default: String): String =
        cache[key] ?: default

    override suspend fun putString(key: String, value: String) {
        cache[key] = value
        save()
    }

    override suspend fun getInt(key: String, default: Int): Int =
        cache[key]?.toIntOrNull() ?: default

    override suspend fun putInt(key: String, value: Int) {
        cache[key] = value.toString()
        save()
    }

    override suspend fun clear() {
        cache.clear()
        save()
    }
}
