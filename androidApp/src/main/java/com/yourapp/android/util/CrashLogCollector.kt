package com.yourapp.android.util

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志收集器
 * 收集崩溃信息并保存到本地文件，下次启动时显示
 */
object CrashLogCollector {

    private const val CRASH_LOG_FILE = "bili_crash_logs.json"
    private const val MAX_LOG_ENTRIES = 50

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class CrashLogEntry(
        val timestamp: String,
        val errorType: String,
        val errorMessage: String,
        val stackTrace: String,
        val threadName: String,
        val context: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class CrashLogFile(
        val entries: List<CrashLogEntry> = emptyList(),
        val appVersion: String = "unknown"
    )

    /**
     * 保存崩溃日志
     */
    fun saveCrashLog(
        context: Context,
        throwable: Throwable,
        extraContext: Map<String, String> = emptyMap()
    ) {
        try {
            val logFile = getLogFile(context)
            val existingLogs = loadLogs(context)

            val newEntry = CrashLogEntry(
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date()),
                errorType = throwable.javaClass.simpleName,
                errorMessage = throwable.message ?: "Unknown error",
                stackTrace = throwable.stackTraceToString(),
                threadName = Thread.currentThread().name,
                context = extraContext
            )

            val updatedLogs = (existingLogs + newEntry).takeLast(MAX_LOG_ENTRIES)
            val logData = CrashLogFile(
                entries = updatedLogs,
                appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                } catch (_: Exception) {
                    "unknown"
                }
            )

            logFile.writeText(json.encodeToString(CrashLogFile.serializer(), logData))
        } catch (e: Exception) {
            android.util.Log.e("CrashLogCollector", "Failed to save crash log", e)
        }
    }

    /**
     * 加载所有崩溃日志
     */
    fun loadLogs(context: Context): List<CrashLogEntry> {
        return try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return emptyList()

            val content = logFile.readText()
            val logData = json.decodeFromString(CrashLogFile.serializer(), content)
            logData.entries
        } catch (e: Exception) {
            android.util.Log.e("CrashLogCollector", "Failed to load crash logs", e)
            emptyList()
        }
    }

    /**
     * 获取最近的崩溃日志（如果有的话）
     */
    fun getLastCrashLog(context: Context): CrashLogEntry? {
        val logs = loadLogs(context)
        return logs.lastOrNull()
    }

    /**
     * 检查是否有崩溃日志
     */
    fun hasCrashLogs(context: Context): Boolean {
        return loadLogs(context).isNotEmpty()
    }

    /**
     * 清除所有崩溃日志
     */
    fun clearLogs(context: Context) {
        try {
            getLogFile(context).delete()
        } catch (e: Exception) {
            android.util.Log.e("CrashLogCollector", "Failed to clear crash logs", e)
        }
    }

    /**
     * 格式化崩溃日志为可读的字符串
     */
    fun formatCrashLogForDisplay(entry: CrashLogEntry): String {
        return buildString {
            appendLine("崩溃时间: ${entry.timestamp}")
            appendLine("错误类型: ${entry.errorType}")
            appendLine("错误信息: ${entry.errorMessage}")
            appendLine("线程: ${entry.threadName}")
            if (entry.context.isNotEmpty()) {
                appendLine("上下文:")
                entry.context.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            appendLine("堆栈跟踪:")
            appendLine(entry.stackTrace.lines().take(20).joinToString("\n"))
            if (entry.stackTrace.lines().size > 20) {
                appendLine("  ... (${entry.stackTrace.lines().size - 20} more lines)")
            }
        }
    }

    private fun getLogFile(context: Context): File {
        return File(context.filesDir, CRASH_LOG_FILE)
    }
}
