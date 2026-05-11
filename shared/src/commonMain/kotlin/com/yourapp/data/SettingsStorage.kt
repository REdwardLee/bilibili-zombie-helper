package com.yourapp.data

// 平台无关的存储接口
// Android 实现用 DataStore/Preferences，Desktop 用本地文件
interface SettingsStorage {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun putString(key: String, value: String)
    suspend fun getInt(key: String, default: Int = 0): Int
    suspend fun putInt(key: String, value: Int)
    suspend fun clear()
}

// Cookie 存储专用 key
object StorageKeys {
    const val BILI_SESSDATA = "bili_sessdata"
    const val BILI_BILI_JCT = "bili_bili_jct"
    const val BILI_UID = "bili_uid"
    const val BILI_DEDEUSERID = "bili_dedeuserid"
}
