package com.yourapp.android.service

import kotlinx.serialization.Serializable

/**
 * 搜索参数配置数据类
 * 所有参数均可通过调试面板动态调整
 */
@Serializable
data class SearchConfig(
    // 延迟档位参数 (getDelayForCount)
    val tier1MaxCount: Int = 20,
    val tier1BaseDelay: Long = 100,
    val tier1RandomDelay: Long = 200,
    val tier2MaxCount: Int = 40,
    val tier2BaseDelay: Long = 500,
    val tier2RandomDelay: Long = 1000,
    val tier3MaxCount: Int = 60,
    val tier3BaseDelay: Long = 1000,
    val tier3RandomDelay: Long = 2000,
    val tier4BaseDelay: Long = 3000,
    val tier4RandomDelay: Long = 2000,

    // 重试延迟参数
    val retryBaseDelay: Long = 60000,
    val retryRandomDelay: Long = 30000,

    // API 预估耗时
    val apiEstimateTimeMs: Long = 500,

    // ETA 计算分段参数
    val etaSegment1AvgTotal: Long = 200 + 500,   // 延迟平均200ms + API 500ms
    val etaSegment2AvgTotal: Long = 1000 + 500,    // 延迟平均1000ms + API 500ms
    val etaSegment3AvgTotal: Long = 2000 + 500,    // 延迟平均2000ms + API 500ms
    val etaSegment4AvgTotal: Long = 4000 + 500,    // 延迟平均4000ms + API 500ms

    // 页大小
    val pageSize: Int = 50
) {
    companion object {
        /** 默认配置 */
        val DEFAULT = SearchConfig()

        /** 从存储的JSON字符串反序列化，失败则返回默认 */
        fun fromJson(json: String): SearchConfig = try {
            kotlinx.serialization.json.Json.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            DEFAULT
        }
    }

    /** 序列化为JSON字符串 */
    fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
}
