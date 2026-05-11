package com.yourapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSettingsStorage(context: Context) : SettingsStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences("bili_app", Context.MODE_PRIVATE)

    override suspend fun getString(key: String, default: String): String =
        withContext(Dispatchers.IO) { prefs.getString(key, default) ?: default }

    override suspend fun putString(key: String, value: String) =
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }

    override suspend fun getInt(key: String, default: Int): Int =
        withContext(Dispatchers.IO) { prefs.getInt(key, default) }

    override suspend fun putInt(key: String, value: Int) =
        withContext(Dispatchers.IO) { prefs.edit().putInt(key, value).apply() }

    override suspend fun clear() =
        withContext(Dispatchers.IO) { prefs.edit().clear().apply() }
}
