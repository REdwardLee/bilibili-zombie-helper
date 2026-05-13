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
    const val BILI_FULL_COOKIE = "bili_full_cookie"
    // 僵尸榜数据持久化
    const val ZOMBIE_FOLLOWINGS = "zombie_followings"
    const val ZOMBIE_FOLLOWERS = "zombie_followers"
    const val SHOW_ZOMBIE_VIEW = "show_zombie_view"
    // 断点续传：上次搜索停止的位置
    const val ZOMBIE_FOLLOWING_LAST_PAGE = "zombie_following_last_page"
    const val ZOMBIE_FOLLOWING_LAST_INDEX = "zombie_following_last_index"
    const val ZOMBIE_FOLLOWER_LAST_PAGE = "zombie_follower_last_page"
    const val ZOMBIE_FOLLOWER_LAST_INDEX = "zombie_follower_last_index"
}
