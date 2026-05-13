package com.yourapp.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yourapp.android.MainActivity
import com.yourapp.data.AndroidSettingsStorage
import com.yourapp.data.BiliRepositoryImpl
import com.yourapp.domain.BiliUser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

class ZombieSearchService : Service() {

    companion object {
        const val ACTION_START_FOLLOWING = "START_FOLLOWING"
        const val ACTION_START_FOLLOWER = "START_FOLLOWER"
        const val ACTION_STOP = "STOP"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_UID = "uid"
        const val CHANNEL_ID = "zombie_search_channel"
        const val NOTIFICATION_ID = 1001

        // 广播 action
        const val BROADCAST_PROGRESS = "com.yourapp.android.ZOMBIE_PROGRESS"
        const val BROADCAST_COMPLETE = "com.yourapp.android.ZOMBIE_COMPLETE"
        const val EXTRA_PROGRESS_TEXT = "progress_text"
        const val EXTRA_SEARCH_TYPE = "search_type"
        const val EXTRA_ITEM_COUNT = "item_count"

        // 暴露状态流给外部观察
        private val _serviceProgress = MutableStateFlow("")
        val serviceProgress: StateFlow<String> = _serviceProgress.asStateFlow()

        private val _serviceEta = MutableStateFlow("")
        val serviceEta: StateFlow<String> = _serviceEta.asStateFlow()

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var searchJob: Job? = null

    private val saveJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ZombieFollowingItem(val user: BiliUser, val timestamp: Long)

    @Serializable
    private data class ZombieFollowingSnapshot(val items: List<ZombieFollowingItem>)

    @Serializable
    private data class ZombieFollowerSnapshot(val items: List<BiliUser>)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOLLOWING -> {
                val cookie = intent.getStringExtra(EXTRA_COOKIE) ?: return START_NOT_STICKY
                val uid = intent.getLongExtra(EXTRA_UID, 0L)
                if (uid == 0L) return START_NOT_STICKY
                startFollowingSearch(cookie, uid)
            }
            ACTION_START_FOLLOWER -> {
                val cookie = intent.getStringExtra(EXTRA_COOKIE) ?: return START_NOT_STICKY
                val uid = intent.getLongExtra(EXTRA_UID, 0L)
                if (uid == 0L) return START_NOT_STICKY
                startFollowerSearch(cookie, uid)
            }
            ACTION_STOP -> {
                stopSearch()
            }
        }
        return START_NOT_STICKY
    }

    private fun getDelayForCount(count: Int): Long {
        return when {
            count <= 20 -> 100L + Random.nextLong(0, 200)
            count <= 40 -> 500L + Random.nextLong(0, 1000)
            count <= 60 -> 1000L + Random.nextLong(0, 2000)
            else -> 3000L + Random.nextLong(0, 2000)
        }
    }

    private fun startFollowingSearch(cookie: String, uid: Long) {
        if (_serviceRunning.value) return
        _serviceRunning.value = true
        _serviceEta.value = ""

        val notification = buildNotification("正在搜索僵尸UP...", "准备中", 0, 0)
        startForeground(NOTIFICATION_ID, notification)

        searchJob = serviceScope.launch {
            val storage = AndroidSettingsStorage(applicationContext)
            val repo = BiliRepositoryImpl(storage)
            val getFollowings = com.yourapp.usecases.GetFollowingsUseCase(repo)
            val getUserLastUpdateTime = com.yourapp.usecases.GetUserLastUpdateTimeUseCase(repo)

            // 获取关注总数
            var totalCount = 0
            repo.getFollowingsTotal(uid).fold(
                onSuccess = { totalCount = it },
                onFailure = { totalCount = 0 }
            )

            val allResults = mutableListOf<Pair<BiliUser, Long>>()
            var page = 1
            val pageSize = 50
            var hasMore = true
            var totalChecked = 0
            var sessionChecked = 0 // 本次搜索的计数，每次重新开始搜索时从0开始
            val sessionStartTime = System.currentTimeMillis()

            // 恢复已有结果和断点
            try {
                val followingJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS, "")
                if (followingJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowingSnapshot.serializer(), followingJson)
                    allResults.addAll(snapshot.items.map { it.user to it.timestamp })
                    totalChecked = allResults.size
                }
            } catch (_: Exception) { }

            // 恢复断点：上次搜索停止的位置
            var startPage = storage.getInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_PAGE, 1)
            var startIndex = storage.getInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_INDEX, -1)
            // 如果上次停在页末，跳到下一页开头
            if (startIndex >= pageSize - 1) {
                startPage++
                startIndex = -1
            }

            // 调整初始 page
            page = startPage

            while (hasMore && isActive) {
                val batch = mutableListOf<BiliUser>()
                getFollowings(uid, page, pageSize).fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) hasMore = false
                        else {
                            batch += list
                            if (list.size < pageSize) hasMore = false
                        }
                    },
                    onFailure = { hasMore = false }
                )

                if (batch.isEmpty()) break

                for ((index, user) in batch.withIndex()) {
                    if (!isActive) break

                    // 断点续传：跳过已检查的部分（仅限断点所在页）
                    if (page == startPage && index <= startIndex) continue

                    // 重试循环：失败后重试同一个UP主
                    var success = false
                    var retries = 0
                    while (!success && isActive) {
                        // 计算延迟：失败后每分钟一次，正常按本次搜索计数分段
                        val delayMs = if (retries > 0) {
                            60000L + Random.nextLong(0, 30000) // 失败重试：每分钟一次
                        } else {
                            getDelayForCount(sessionChecked) // 本次搜索的分段节奏
                        }

                        if (sessionChecked > 0 || page > startPage) {
                            delay(delayMs)
                        }

                        val checkNum = totalChecked + 1
                        val progressText = if (retries > 0) {
                            "重试 ${user.uname} (第${retries}次, ${checkNum}/${totalCount})..."
                        } else {
                            "正在检查 ${user.uname} (${checkNum}/${totalCount})..."
                        }
                        _serviceProgress.value = progressText
                        updateNotification("正在搜索僵尸UP", progressText, checkNum, totalCount.coerceAtLeast(1))
                        sendProgressBroadcast(1, progressText, checkNum)

                        getUserLastUpdateTime(user.mid).fold(
                            onSuccess = { timestamp ->
                                val existing = allResults.indexOfFirst { it.first.mid == user.mid }
                                if (existing >= 0) {
                                    allResults[existing] = user to timestamp
                                } else {
                                    allResults.add(user to timestamp)
                                }
                                success = true
                                totalChecked++
                                sessionChecked++ // 本次搜索计数增加

                                // 更新预估时间（基于分段延迟理论值）
                                if (totalCount > 0) {
                                    val etaMs = estimateRemainingTime(sessionChecked, totalCount)
                                    _serviceEta.value = formatEta(etaMs)
                                }
                            },
                            onFailure = {
                                retries++
                                // 不增加 totalChecked 和 sessionChecked，重试同一个
                            }
                        )

                        if (success) {
                            // 排序并保存
                            val sorted = allResults.sortedBy { it.second }
                            saveFollowingResults(storage, sorted)
                            // 保存断点：当前页和索引
                            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_PAGE, page)
                            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_INDEX, index)
                        }
                    }
                }
                page++
            }

            // 搜索完成，清除断点
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_PAGE, 1)
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_INDEX, -1)

            val finalText = "已完成 ${allResults.size} 个UP主"
            _serviceProgress.value = finalText
            _serviceEta.value = ""
            updateNotification("搜索完成", finalText, 100, 100)
            sendCompleteBroadcast(1, allResults.size)

            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            _serviceRunning.value = false
        }
    }

    private fun startFollowerSearch(cookie: String, uid: Long) {
        if (_serviceRunning.value) return
        _serviceRunning.value = true

        val notification = buildNotification("正在搜索僵尸粉...", "准备中", 0, 0)
        startForeground(NOTIFICATION_ID, notification)

        searchJob = serviceScope.launch {
            val storage = AndroidSettingsStorage(applicationContext)
            val repo = BiliRepositoryImpl(storage)
            val getFollowers = com.yourapp.usecases.GetFollowersUseCase(repo)

            // 获取粉丝总数
            var totalCount = 0
            repo.getFollowersTotal(uid).fold(
                onSuccess = { totalCount = it },
                onFailure = { totalCount = 0 }
            )

            val allResults = mutableListOf<BiliUser>()
            var page = 1
            val pageSize = 50
            var hasMore = true
            var totalChecked = 0

            // 恢复已有结果和断点
            try {
                val followerJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS, "")
                if (followerJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowerSnapshot.serializer(), followerJson)
                    allResults.addAll(snapshot.items)
                    totalChecked = allResults.size
                }
            } catch (_: Exception) { }

            // 恢复断点
            var startPage = storage.getInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_PAGE, 1)
            var startIndex = storage.getInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_INDEX, -1)
            if (startIndex >= pageSize - 1) {
                startPage++
                startIndex = -1
            }
            page = startPage

            while (hasMore && isActive) {
                val batch = mutableListOf<BiliUser>()
                getFollowers(uid, page, pageSize).fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) hasMore = false
                        else {
                            batch += list
                            if (list.size < pageSize) hasMore = false
                        }
                    },
                    onFailure = { hasMore = false }
                )

                if (batch.isEmpty()) break

                for ((index, user) in batch.withIndex()) {
                    if (!isActive) break

                    // 断点续传：跳过已检查的部分
                    if (page == startPage && index <= startIndex) continue

                    // 粉丝搜索无限制，快速处理
                    totalChecked++
                    val progressText = "正在检查 ${user.uname} (${totalChecked}/${totalCount})..."
                    _serviceProgress.value = progressText
                    updateNotification("正在搜索僵尸粉", progressText, totalChecked, totalCount.coerceAtLeast(1))
                    sendProgressBroadcast(2, progressText, totalChecked)

                    allResults.add(user)
                    val sorted = allResults.sortedByDescending { isDefaultUsername(it.uname) }
                    saveFollowerResults(storage, sorted)
                    // 保存断点
                    storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_PAGE, page)
                    storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_INDEX, index)
                }
                page++
            }

            // 搜索完成，清除断点
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_PAGE, 1)
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_INDEX, -1)

            val finalText = "已完成 ${allResults.size} 个粉丝"
            _serviceProgress.value = finalText
            updateNotification("搜索完成", finalText, 100, 100)
            sendCompleteBroadcast(2, allResults.size)

            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            _serviceRunning.value = false
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _serviceRunning.value = false
        _serviceProgress.value = "已停止"
        _serviceEta.value = ""
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 格式化预估剩余时间 */
    private fun formatEta(etaMs: Long): String {
        return when {
            etaMs <= 0 -> ""
            etaMs < 60000 -> "预计还需 ${etaMs / 1000} 秒"
            etaMs < 3600000 -> "预计还需 ${etaMs / 60000} 分钟"
            else -> {
                val hours = etaMs / 3600000
                val minutes = (etaMs % 3600000) / 60000
                "预计还需 ${hours} 小时 ${minutes} 分钟"
            }
        }
    }

    /** 基于分段延迟理论值计算剩余时间 */
    private fun estimateRemainingTime(sessionChecked: Int, totalCount: Int): Long {
        var remaining = totalCount - sessionChecked
        if (remaining <= 0) return 0

        var currentIdx = sessionChecked
        var totalMs = 0L
        val apiTimeMs = 500L // API请求+处理约500ms

        // 段定义：(起始, 结束, 平均总耗时=延迟+API)
        val segments = listOf(
            Triple(0, 20, 200L + apiTimeMs),      // 0-19: 延迟平均200ms
            Triple(20, 40, 1000L + apiTimeMs),     // 20-39: 延迟平均1000ms
            Triple(40, 60, 2000L + apiTimeMs),     // 40-59: 延迟平均2000ms
            Triple(60, Int.MAX_VALUE, 4000L + apiTimeMs) // 60+: 延迟平均4000ms
        )

        for ((start, end, avgTotal) in segments) {
            if (currentIdx >= end) continue

            val countInSegment = minOf(end - currentIdx, remaining)
            totalMs += countInSegment * avgTotal
            remaining -= countInSegment
            currentIdx += countInSegment

            if (remaining <= 0) break
        }

        return totalMs
    }

    private fun saveFollowingResults(storage: AndroidSettingsStorage, results: List<Pair<BiliUser, Long>>) {
        serviceScope.launch {
            try {
                val snapshot = ZombieFollowingSnapshot(results.map { ZombieFollowingItem(it.first, it.second) })
                storage.putString(
                    com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS,
                    saveJson.encodeToString(ZombieFollowingSnapshot.serializer(), snapshot)
                )
            } catch (_: Exception) { }
        }
    }

    private fun saveFollowerResults(storage: AndroidSettingsStorage, results: List<BiliUser>) {
        serviceScope.launch {
            try {
                val snapshot = ZombieFollowerSnapshot(results)
                storage.putString(
                    com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS,
                    saveJson.encodeToString(ZombieFollowerSnapshot.serializer(), snapshot)
                )
            } catch (_: Exception) { }
        }
    }

    private fun sendProgressBroadcast(searchType: Int, text: String, count: Int) {
        sendBroadcast(Intent(BROADCAST_PROGRESS).apply {
            putExtra(EXTRA_SEARCH_TYPE, searchType)
            putExtra(EXTRA_PROGRESS_TEXT, text)
            putExtra(EXTRA_ITEM_COUNT, count)
            setPackage(packageName)
        })
    }

    private fun sendCompleteBroadcast(searchType: Int, count: Int) {
        sendBroadcast(Intent(BROADCAST_COMPLETE).apply {
            putExtra(EXTRA_SEARCH_TYPE, searchType)
            putExtra(EXTRA_ITEM_COUNT, count)
            setPackage(packageName)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "僵尸搜索",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示搜索僵尸UP/僵尸粉的进度"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String, progress: Int, max: Int): Notification {
        val stopIntent = Intent(this, ZombieSearchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
            .apply {
                if (max > 0) {
                    setProgress(max, progress, false)
                }
            }
            .build()
    }

    private fun updateNotification(title: String, content: String, progress: Int, max: Int) {
        val notification = buildNotification(title, content, progress, max)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _serviceRunning.value = false
    }
}

/** 判断是否默认用户名 */
fun isDefaultUsername(uname: String): Boolean {
    return uname.startsWith("bili_") || uname.matches(Regex("^\\d+$"))
}
