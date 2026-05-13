package com.yourapp.android.di

import com.yourapp.android.BuildConfig

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.data.AndroidSettingsStorage
import com.yourapp.data.BiliRepositoryImpl
import com.yourapp.domain.BiliUser
import com.yourapp.usecases.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 序列化辅助类 */
@Serializable
private data class ZombieFollowingItem(val user: BiliUser, val timestamp: Long)

@Serializable
private data class ZombieFollowingSnapshot(val items: List<ZombieFollowingItem>)

@Serializable
private data class ZombieFollowerSnapshot(val items: List<BiliUser>)

private val saveJson = Json { ignoreUnknownKeys = true }

class AppViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val storage = AndroidSettingsStorage(appContext)
    private val repo = BiliRepositoryImpl(storage)

    private val getLoginInfo = GetLoginInfoUseCase(repo)
    private val getFollowings = GetFollowingsUseCase(repo)
    private val getFollowers = GetFollowersUseCase(repo)
    private val getUserLastUpdateTime = GetUserLastUpdateTimeUseCase(repo)
    private val getRelationStatus = GetRelationStatusUseCase(repo)
    private val modifyRelation = ModifyRelationUseCase(repo)
    private val saveCookies = SaveCookiesUseCase(repo)
    private val logoutUseCase = LogoutUseCase(repo)
    private val checkLogin = IsLoggedInUseCase(repo)

    // UI State
    private val _user = MutableStateFlow<BiliUser?>(null)
    val user: StateFlow<BiliUser?> = _user.asStateFlow()

    private val _followings = MutableStateFlow<List<BiliUser>>(emptyList())
    val followings: StateFlow<List<BiliUser>> = _followings.asStateFlow()

    private val _followers = MutableStateFlow<List<BiliUser>>(emptyList())
    val followers: StateFlow<List<BiliUser>> = _followers.asStateFlow()

    // 分页状态
    private val _followingPage = MutableStateFlow(1)
    private val _hasMoreFollowings = MutableStateFlow(true)
    val hasMoreFollowings: StateFlow<Boolean> = _hasMoreFollowings.asStateFlow()

    private val _followerPage = MutableStateFlow(1)
    private val _hasMoreFollowers = MutableStateFlow(true)
    val hasMoreFollowers: StateFlow<Boolean> = _hasMoreFollowers.asStateFlow()

    // 搜索状态（拆分为两个独立的搜索）
    private val _isSearchingFollowings = MutableStateFlow(false)
    val isSearchingFollowings: StateFlow<Boolean> = _isSearchingFollowings.asStateFlow()

    private val _isSearchingFollowers = MutableStateFlow(false)
    val isSearchingFollowers: StateFlow<Boolean> = _isSearchingFollowers.asStateFlow()

    private val _searchType = MutableStateFlow(0) // 0=无, 1=关注, 2=粉丝
    val searchType: StateFlow<Int> = _searchType.asStateFlow()

    private val _zombieFollowings = MutableStateFlow<List<Pair<BiliUser, Long>>>(emptyList())
    val zombieFollowings: StateFlow<List<Pair<BiliUser, Long>>> = _zombieFollowings.asStateFlow()

    private val _zombieFollowers = MutableStateFlow<List<BiliUser>>(emptyList())
    val zombieFollowers: StateFlow<List<BiliUser>> = _zombieFollowers.asStateFlow()

    // 僵尸UP搜索进度（支持继续搜索）
    private val _followingSearchPage = MutableStateFlow(1)
    private val _followingSearchHasMore = MutableStateFlow(true)

    // 僵尸粉搜索进度（支持继续搜索）
    private val _followerSearchPage = MutableStateFlow(1)
    private val _followerSearchHasMore = MutableStateFlow(true)

    private val _followingSearchProgress = MutableStateFlow("")
    val followingSearchProgress: StateFlow<String> = _followingSearchProgress.asStateFlow()

    private val _followerSearchProgress = MutableStateFlow("")
    val followerSearchProgress: StateFlow<String> = _followerSearchProgress.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isRecheckingFailed = MutableStateFlow(false)
    val isRecheckingFailed: StateFlow<Boolean> = _isRecheckingFailed.asStateFlow()

    private val _recheckProgress = MutableStateFlow("")
    val recheckProgress: StateFlow<String> = _recheckProgress.asStateFlow()

    // 调试日志（DEBUG 版本才有效）
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    private val _showDebugOverlay = MutableStateFlow(false)
    val showDebugOverlay: StateFlow<Boolean> = _showDebugOverlay.asStateFlow()

    // 最近一次保存的 HTML 文件路径（被风控时保存）
    private val _lastHtmlDir = MutableStateFlow<String?>(null)
    val lastHtmlDir: StateFlow<String?> = _lastHtmlDir.asStateFlow()

    fun toggleDebugOverlay() {
        if (BuildConfig.DEBUG) _showDebugOverlay.value = !_showDebugOverlay.value
    }

    fun clearDebugLogs() {
        if (BuildConfig.DEBUG) _debugLogs.value = emptyList()
    }

    /** 打开日志保存目录（目录打开失败时 fallback 到打开最近文件） */
    fun openHtmlDirectory(): android.content.Intent? {
        val dir = java.io.File(appContext.getExternalFilesDir(null), "debug_html")
        if (!dir.exists()) dir.mkdirs()
        
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                dir
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // 如果没有应用能处理目录，fallback 到打开最近一个文件
            if (intent.resolveActivity(appContext.packageManager) == null) {
                val lastFile = dir.listFiles()?.maxByOrNull { it.lastModified() }
                if (lastFile != null) {
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        lastFile
                    )
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, "text/html")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else null
            } else intent
        } catch (e: Exception) {
            // 异常时 fallback
            val lastFile = dir.listFiles()?.maxByOrNull { it.lastModified() }
            if (lastFile != null) {
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    lastFile
                )
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "text/html")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else null
        }
    }

    private fun addDebugLog(log: String) {
        if (!BuildConfig.DEBUG) return
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        _debugLogs.value = (_debugLogs.value + "[$timestamp] $log").takeLast(100)
    }

    /** 保存 HTML 内容到外部文件，返回文件路径 */
    private fun saveHtmlToFile(html: String, mid: Long): String {
        val dir = java.io.File(appContext.getExternalFilesDir(null), "debug_html")
        dir.mkdirs()
        val file = java.io.File(dir, "relation_${mid}_${System.currentTimeMillis()}.html")
        file.writeText(html)
        _lastHtmlDir.value = dir.absolutePath
        return file.absolutePath
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        checkLoginStatus()
        restoreZombieData()
    }

    /** 从本地存储恢复上次僵尸榜数据 */
    private fun restoreZombieData() {
        viewModelScope.launch {
            try {
                // 恢复关注僵尸榜
                val followingJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS, "")
                if (followingJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowingSnapshot.serializer(), followingJson)
                    _zombieFollowings.value = snapshot.items.map { it.user to it.timestamp }
                }

                // 恢复粉丝僵尸榜
                val followerJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS, "")
                if (followerJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowerSnapshot.serializer(), followerJson)
                    _zombieFollowers.value = snapshot.items
                }

                // 恢复视图状态
                val showZombie = storage.getInt(com.yourapp.data.StorageKeys.SHOW_ZOMBIE_VIEW, 0) == 1
                _showZombieView.value = showZombie

                // 如果有恢复的数据，标记 searchType
                if (_zombieFollowings.value.isNotEmpty()) {
                    _searchType.value = _searchType.value or 1
                }
                if (_zombieFollowers.value.isNotEmpty()) {
                    _searchType.value = _searchType.value or 2
                }
            } catch (_: Exception) {
                // 恢复失败静默忽略
            }
        }
    }

    /** 保存僵尸榜数据到本地 */
    private fun saveZombieData() {
        viewModelScope.launch {
            try {
                // 保存关注僵尸榜
                if (_zombieFollowings.value.isNotEmpty()) {
                    val snapshot = ZombieFollowingSnapshot(
                        _zombieFollowings.value.map { ZombieFollowingItem(it.first, it.second) }
                    )
                    storage.putString(
                        com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS,
                        saveJson.encodeToString(ZombieFollowingSnapshot.serializer(), snapshot)
                    )
                }

                // 保存粉丝僵尸榜
                if (_zombieFollowers.value.isNotEmpty()) {
                    val snapshot = ZombieFollowerSnapshot(_zombieFollowers.value)
                    storage.putString(
                        com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS,
                        saveJson.encodeToString(ZombieFollowerSnapshot.serializer(), snapshot)
                    )
                }

                // 保存视图状态
                storage.putInt(
                    com.yourapp.data.StorageKeys.SHOW_ZOMBIE_VIEW,
                    if (_showZombieView.value) 1 else 0
                )
            } catch (_: Exception) {
                // 保存失败静默忽略
            }
        }
    }

    fun refreshLoginStatus() = checkLoginStatus()

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val hasCookie = checkLogin().first()
            if (hasCookie) {
                getLoginInfo().fold(
                    onSuccess = {
                        _user.value = it
                        _isLoggedIn.value = true
                        // 自动加载第一批
                        loadFollowings()
                        loadFollowers()
                    },
                    onFailure = {
                        logoutUseCase()
                        _isLoggedIn.value = false
                        _user.value = null
                    }
                )
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    fun saveCookieAndLogin(cookieString: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                saveCookies(cookieString)
                getLoginInfo().fold(
                    onSuccess = {
                        _user.value = it
                        _isLoggedIn.value = true
                    },
                    onFailure = {
                        _error.value = "获取登录信息失败: ${it.message}"
                        _isLoggedIn.value = false
                    }
                )
            } catch (e: Exception) {
                _error.value = "登录失败: ${e.message}"
                _isLoggedIn.value = false
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            getLoginInfo().fold(
                onSuccess = { _user.value = it },
                onFailure = { _error.value = "获取用户信息失败: ${it.message}" }
            )
            _loading.value = false
        }
    }

    fun loadFollowings(loadMore: Boolean = false) {
        viewModelScope.launch {
            val uid = _user.value?.mid ?: return@launch
            _loading.value = true
            _error.value = null

            val page = if (loadMore) _followingPage.value + 1 else 1
            val pageSize = 50

            getFollowings(uid, page, pageSize).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        _hasMoreFollowings.value = false
                    } else {
                        if (loadMore) {
                            _followings.value += list
                        } else {
                            _followings.value = list
                        }
                        _followingPage.value = page
                        _hasMoreFollowings.value = list.size >= pageSize

                        // 同步关注列表的权威 attribute 到僵尸榜：只要UP在关注列表中，就强制设为已关注
                        val followingMap = _followings.value.associateBy { it.mid }
                        val updatedZombie = _zombieFollowings.value.map { (user, ts) ->
                            val auth = followingMap[user.mid]
                            if (auth != null && user.attribute < 2) {
                                // 在关注列表中但僵尸榜显示未关注：强制修正为已关注
                                user.copy(attribute = 2) to ts
                            } else {
                                user to ts
                            }
                        }
                        if (updatedZombie != _zombieFollowings.value) {
                            _zombieFollowings.value = updatedZombie
                            saveZombieData()
                        }
                    }
                },
                onFailure = {
                    _error.value = "获取关注列表失败: ${it.message}"
                }
            )
            _loading.value = false
        }
    }

    fun loadFollowers(loadMore: Boolean = false) {
        viewModelScope.launch {
            val uid = _user.value?.mid ?: return@launch
            _loading.value = true
            _error.value = null

            val page = if (loadMore) _followerPage.value + 1 else 1
            val pageSize = 50

            getFollowers(uid, page, pageSize).fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        _hasMoreFollowers.value = false
                    } else {
                        if (loadMore) {
                            _followers.value += list
                        } else {
                            _followers.value = list
                        }
                        _followerPage.value = page
                        _hasMoreFollowers.value = list.size >= pageSize
                    }
                },
                onFailure = {
                    _error.value = "获取粉丝列表失败: ${it.message}"
                }
            )
            _loading.value = false
        }
    }

    /** 开始搜索僵尸UP（通过前台服务后台运行） */
    fun startZombieFollowingSearch(continueFromLast: Boolean = false) {
        val uid = _user.value?.mid ?: return
        _showZombieView.value = true
        _searchType.value = _searchType.value or 1

        // 清空旧结果（如果不是继续搜索）
        if (!continueFromLast) {
            _zombieFollowings.value = emptyList()
            _followingSearchPage.value = 1
            _followingSearchHasMore.value = true
            viewModelScope.launch {
                storage.putString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS, "")
                // 清除断点：从头开始搜索
                storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_PAGE, 1)
                storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_INDEX, -1)
            }
        }

        val cookie = runBlocking { storage.getString(com.yourapp.data.StorageKeys.BILI_FULL_COOKIE, "") }
        if (cookie.isBlank()) return

        val intent = android.content.Intent(appContext, com.yourapp.android.service.ZombieSearchService::class.java).apply {
            action = com.yourapp.android.service.ZombieSearchService.ACTION_START_FOLLOWING
            putExtra(com.yourapp.android.service.ZombieSearchService.EXTRA_COOKIE, cookie)
            putExtra(com.yourapp.android.service.ZombieSearchService.EXTRA_UID, uid)
        }
        appContext.startService(intent)
        _isSearchingFollowings.value = true
    }

    /** 开始搜索僵尸粉（通过前台服务后台运行） */
    fun startZombieFollowerSearch(continueFromLast: Boolean = false) {
        val uid = _user.value?.mid ?: return
        _showZombieView.value = true
        _searchType.value = _searchType.value or 2

        if (!continueFromLast) {
            _zombieFollowers.value = emptyList()
            _followerSearchPage.value = 1
            _followerSearchHasMore.value = true
            viewModelScope.launch {
                storage.putString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS, "")
                // 清除断点：从头开始搜索
                storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_PAGE, 1)
                storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_INDEX, -1)
            }
        }

        val cookie = runBlocking { storage.getString(com.yourapp.data.StorageKeys.BILI_FULL_COOKIE, "") }
        if (cookie.isBlank()) return

        val intent = android.content.Intent(appContext, com.yourapp.android.service.ZombieSearchService::class.java).apply {
            action = com.yourapp.android.service.ZombieSearchService.ACTION_START_FOLLOWER
            putExtra(com.yourapp.android.service.ZombieSearchService.EXTRA_COOKIE, cookie)
            putExtra(com.yourapp.android.service.ZombieSearchService.EXTRA_UID, uid)
        }
        appContext.startService(intent)
        _isSearchingFollowers.value = true
    }

    /** 停止搜索 */
    fun stopFollowingSearch() {
        val intent = android.content.Intent(appContext, com.yourapp.android.service.ZombieSearchService::class.java).apply {
            action = com.yourapp.android.service.ZombieSearchService.ACTION_STOP
        }
        appContext.startService(intent)
        _isSearchingFollowings.value = false
    }

    fun stopFollowerSearch() {
        val intent = android.content.Intent(appContext, com.yourapp.android.service.ZombieSearchService::class.java).apply {
            action = com.yourapp.android.service.ZombieSearchService.ACTION_STOP
        }
        appContext.startService(intent)
        _isSearchingFollowers.value = false
    }

    /** 从本地存储重新加载僵尸数据（Service 后台保存后调用）
     * 合并策略：保留内存中已有项的最新状态（attribute可能已被手动修改），只添加新发现的项
     */
    fun reloadZombieData() {
        viewModelScope.launch {
            try {
                val followingJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS, "")
                if (followingJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowingSnapshot.serializer(), followingJson)
                    val loaded = snapshot.items.map { it.user to it.timestamp }

                    // 合并：保留内存中已有项（attribute可能已被手动修改），只添加新项
                    val currentMap = _zombieFollowings.value.associateBy { it.first.mid }
                    val merged = loaded.map { (user, ts) ->
                        val existing = currentMap[user.mid]
                        if (existing != null) {
                            existing // 保留内存版本（包含最新的attribute状态）
                        } else {
                            user to ts // 新发现的僵尸UP
                        }
                    }
                    _zombieFollowings.value = merged
                    _searchType.value = _searchType.value or 1
                }

                val followerJson = storage.getString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS, "")
                if (followerJson.isNotBlank()) {
                    val snapshot = saveJson.decodeFromString(ZombieFollowerSnapshot.serializer(), followerJson)
                    val loaded = snapshot.items

                    // 合并：保留内存中已有项的最新attribute
                    val currentMap = _zombieFollowers.value.associateBy { it.mid }
                    val merged = loaded.map { user ->
                        currentMap[user.mid] ?: user // 保留内存版本，或添加新项
                    }
                    _zombieFollowers.value = merged
                    _searchType.value = _searchType.value or 2
                }
            } catch (_: Exception) { }
        }
    }

    /** 刷新当前显示列表（普通列表刷新数据，僵尸榜清空重新搜索） */
    fun refreshCurrentList(selectedTab: Int, showZombieView: Boolean) {
        if (showZombieView) {
            if (selectedTab == 0) {
                _zombieFollowings.value = emptyList()
                startZombieFollowingSearch(continueFromLast = false)
            } else {
                _zombieFollowers.value = emptyList()
                startZombieFollowerSearch(continueFromLast = false)
            }
        } else {
            if (selectedTab == 0) {
                _followingPage.value = 1
                _hasMoreFollowings.value = true
                loadFollowings(loadMore = false)
            } else {
                _followerPage.value = 1
                _hasMoreFollowers.value = true
                loadFollowers(loadMore = false)
            }
        }
    }

    /** 单独排查僵尸榜中"无法获取更新时间"的UP主 */
    fun recheckFailedFollowings() {
        viewModelScope.launch {
            val toRecheck = _zombieFollowings.value.filter { it.second == 0L }
            if (toRecheck.isEmpty()) return@launch

            _isRecheckingFailed.value = true
            val total = toRecheck.size
            val updated = mutableListOf<Pair<BiliUser, Long>>()

            for ((idx, pair) in toRecheck.withIndex()) {
                val (user, _) = pair
                _recheckProgress.value = "排查中 ${user.uname} (${idx + 1}/${total})..."

                getUserLastUpdateTime(user.mid).fold(
                    onSuccess = { timestamp ->
                        updated.add(user to timestamp)
                    },
                    onFailure = {
                        // 仍然失败，保持原值
                        updated.add(user to pair.second)
                    }
                )

                // 延迟避免被限流
                if (idx < total - 1) {
                    delay(800L + Random.nextLong(0, 400))
                }
            }

            // 替换原列表中的对应项
            val newList = _zombieFollowings.value.map { existing ->
                val match = updated.find { it.first.mid == existing.first.mid }
                match ?: existing
            }
            _zombieFollowings.value = newList
            saveZombieData()

            val recovered = updated.count { it.second != 0L }
            val stillFailed = updated.count { it.second == 0L }
            _recheckProgress.value = "排查完成: $recovered 个恢复, $stillFailed 个仍失败"
            _isRecheckingFailed.value = false
        }
    }

    /** 清理前校验：对僵尸榜中 attribute==0 的UP查询真实关注状态，修正误标的，返回真正已取关的数量 */
    fun preCheckUnfollowedStatus(onComplete: (realUnfollowedCount: Int) -> Unit) {
        viewModelScope.launch {
            val candidates = _zombieFollowings.value.filter { it.first.attribute == 0 }
            if (candidates.isEmpty()) {
                onComplete(0)
                return@launch
            }

            _isRecheckingFailed.value = true
            var fixed = 0

            for ((idx, pair) in candidates.withIndex()) {
                val (user, ts) = pair
                _recheckProgress.value = "校验关注状态 ${user.uname} (${idx + 1}/${candidates.size})..."

                getRelationStatus(user.mid).fold(
                    onSuccess = { realAttr ->
                        if (realAttr >= 2 && user.attribute < 2) {
                            // 实际还关注着，修正为已关注
                            val updated = user.copy(attribute = realAttr)
                            _zombieFollowings.value = _zombieFollowings.value.map {
                                if (it.first.mid == user.mid) updated to ts else it
                            }
                            fixed++
                        }
                    },
                    onFailure = { }
                )

                if (idx < candidates.size - 1) {
                    delay(800L + Random.nextLong(0, 400))
                }
            }

            saveZombieData()
            _isRecheckingFailed.value = false
            val realUnfollowed = candidates.size - fixed
            onComplete(realUnfollowed)
        }
    }

    /** 通过批量获取关注列表来校准僵尸榜（绕过 getRelationStatus 风控） */
    fun batchCalibrateByFollowingList(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val uid = _user.value?.mid ?: return@launch
            addDebugLog("开始通过关注列表批量校准...")

            // 分页获取所有关注
            val allFollowings = mutableSetOf<Long>()
            var page = 1
            while (true) {
                val result = getFollowings(uid, page, 50)
                val list = result.getOrNull()
                if (list == null) {
                    addDebugLog("获取关注列表第$page 页失败")
                    break
                }
                allFollowings.addAll(list.map { it.mid })
                addDebugLog("获取关注列表第$page 页: ${list.size} 个")
                if (list.size < 50) break
                page++
                delay(500) // 分页间隔防限流
            }

            addDebugLog("共获取 ${allFollowings.size} 个关注")

            // 对比僵尸榜并修正
            val zombieList = _zombieFollowings.value
            var fixed = 0
            val updated = zombieList.map { (user, ts) ->
                val actuallyFollowing = user.mid in allFollowings
                val currentlyShown = user.attribute >= 2
                if (currentlyShown != actuallyFollowing) {
                    fixed++
                    val newAttr = if (actuallyFollowing) 2 else 0
                    val from = if (currentlyShown) "已关注" else "未关注"
                    val to = if (actuallyFollowing) "已关注" else "未关注"
                    addDebugLog("✅ ${user.uname}: $from → $to（已修正）")
                    user.copy(attribute = newAttr) to ts
                } else {
                    user to ts
                }
            }

            _zombieFollowings.value = updated
            saveZombieData()
            addDebugLog("校准完成：获取了 ${allFollowings.size} 个关注，修正了 $fixed 个")
            onComplete()
        }
    }

    /** 记录最后点击跳转的 UP 主 mid（用于返回时校准） */
    private val _lastNavigatedMid = MutableStateFlow<Long?>(null)

    /** 点击 UP 主名字跳转时调用 */
    fun onUpNameClicked(mid: Long) {
        _lastNavigatedMid.value = mid
    }

    /** App 从后台恢复时调用（Lifecycle ON_RESUME） */
    fun onAppResumed() {
        val mid = _lastNavigatedMid.value ?: return
        _lastNavigatedMid.value = null
        addDebugLog("🔄 从浏览器返回，校准 mid=$mid")
        // 使用 getRelationStatus 快速查询（单条，不易触发风控）
        viewModelScope.launch {
            delay(300) // 稍等片刻确保网络恢复
            getRelationStatus(mid).fold(
                onSuccess = { realAttr ->
                    val zombieMap = _zombieFollowings.value.associateBy { it.first.mid }
                    val pair = zombieMap[mid]
                    if (pair != null) {
                        val (user, ts) = pair
                        val currentlyShown = user.attribute >= 2
                        val actuallyFollowing = realAttr >= 2
                        if (currentlyShown != actuallyFollowing) {
                            val updated = user.copy(attribute = if (actuallyFollowing) 2 else 0)
                            _zombieFollowings.value = _zombieFollowings.value.map {
                                if (it.first.mid == mid) updated to ts else it
                            }
                            saveZombieData()
                            val from = if (currentlyShown) "已关注" else "未关注"
                            val to = if (actuallyFollowing) "已关注" else "未关注"
                            addDebugLog("✅ ${user.uname}: $from → $to（返回校准）")
                        } else {
                            addDebugLog("✅ ${user.uname}: 状态一致（${if (actuallyFollowing) "已关注" else "未关注"}）")
                        }
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "未知错误"
                    when {
                        msg.contains("HTML_BLOCK") -> {
                            addDebugLog("❌ mid=$mid: 被风控，跳过校准")
                        }
                        msg.contains("code=-404") || msg.contains("code=22007") || msg.contains("用户不存在") -> {
                            // 用户已注销/不存在，从僵尸榜移除
                            val zombieMap = _zombieFollowings.value.associateBy { it.first.mid }
                            val user = zombieMap[mid]?.first
                            _zombieFollowings.value = _zombieFollowings.value.filter { it.first.mid != mid }
                            saveZombieData()
                            addDebugLog("🗑️ ${user?.uname ?: "mid=$mid"}: 用户不存在，已移除")
                        }
                        else -> {
                            addDebugLog("❌ mid=$mid: 查询失败 ($msg)")
                        }
                    }
                }
            )
        }
    }

    // 可见项关注状态校准（冷却5秒）
    private val _followStatusCooldown = mutableMapOf<Long, Long>()
    // 当前屏幕可见的UP主mid列表（供手动校准按钮使用）
    private val _visibleMids = MutableStateFlow<List<Long>>(emptyList())
    val visibleMids: StateFlow<List<Long>> = _visibleMids.asStateFlow()

    /** 更新当前屏幕可见的UP主 */
    fun updateVisibleMids(mids: List<Long>) {
        _visibleMids.value = mids
    }

    /** 手动校准当前可见UP — 改用 WebView 打开主页检测 */
    fun calibrateVisible() {
        val mids = _visibleMids.value
        if (mids.isNotEmpty()) {
            startWebViewCalibration(mids)
        }
    }

    // ========== WebView 校准系统 ==========
    private val _webViewQueue = MutableStateFlow<List<Long>>(emptyList())
    val webViewQueue: StateFlow<List<Long>> = _webViewQueue.asStateFlow()

    private val _webViewCurrentMid = MutableStateFlow<Long?>(null)
    val webViewCurrentMid: StateFlow<Long?> = _webViewCurrentMid.asStateFlow()

    private val _webViewProcessing = MutableStateFlow(false)
    val webViewProcessing: StateFlow<Boolean> = _webViewProcessing.asStateFlow()

    /** 启动 WebView 校准队列 */
    fun startWebViewCalibration(mids: List<Long>) {
        if (_webViewProcessing.value) {
            addDebugLog("WebView 校准已在运行，跳过")
            return
        }
        _webViewQueue.value = mids
        _webViewProcessing.value = true
        addDebugLog("WebView 校准启动：${mids.size} 个UP")
        processNextWebViewItem()
    }

    /** WebView 加载完成后的回调 */
    fun onWebViewPageFinished(mid: Long) {
        // JS 会在页面加载后自动检测并回调 reportWebViewResult
    }

    /** JS Bridge 回调：报告关注状态（带 mid 校验） */
    fun reportWebViewResult(mid: Long, isFollowing: Boolean) {
        // 校验：如果当前处理的不是这个 mid，忽略（旧 WebView 的延迟回调）
        if (_webViewCurrentMid.value != mid) {
            addDebugLog("⚠️ 忽略延迟回调: mid=$mid, 当前=${_webViewCurrentMid.value}")
            return
        }

        val zombieMap = _zombieFollowings.value.associateBy { it.first.mid }
        val pair = zombieMap[mid] ?: return
        val (user, ts) = pair
        val currentlyShown = user.attribute >= 2
        val actuallyFollowing = isFollowing

        if (currentlyShown != actuallyFollowing) {
            val updated = user.copy(attribute = if (actuallyFollowing) 2 else 0)
            _zombieFollowings.value = _zombieFollowings.value.map {
                if (it.first.mid == mid) updated to ts else it
            }
            saveZombieData()
            val from = if (currentlyShown) "已关注" else "未关注"
            val to = if (actuallyFollowing) "已关注" else "未关注"
            addDebugLog("✅ ${user.uname}: $from → $to（WebView校准）")
        } else {
            val status = if (actuallyFollowing) "已关注" else "未关注"
            addDebugLog("✅ ${user.uname}: $status（WebView一致）")
        }

        // 延迟后处理下一个
        viewModelScope.launch {
            delay(1000)
            processNextWebViewItem()
        }
    }

    /** 处理队列中的下一个 */
    fun processNextWebViewItem() {
        val queue = _webViewQueue.value
        if (queue.isEmpty()) {
            _webViewProcessing.value = false
            _webViewCurrentMid.value = null
            addDebugLog("WebView 校准完成")
            return
        }
        val next = queue.first()
        _webViewQueue.value = queue.drop(1)
        _webViewCurrentMid.value = next
        val user = _zombieFollowings.value.find { it.first.mid == next }?.first
        addDebugLog("🔍 WebView 加载 ${user?.uname ?: "mid=$next"}...")
    }

    /** 获取完整 cookie 字符串供 WebView 注入 */
    fun getWebViewCookie(): String = runBlocking {
        val sessdata = storage.getString(com.yourapp.data.StorageKeys.BILI_SESSDATA)
        val biliJct = storage.getString(com.yourapp.data.StorageKeys.BILI_BILI_JCT)
        val dede = storage.getString(com.yourapp.data.StorageKeys.BILI_DEDEUSERID)
        val full = storage.getString(com.yourapp.data.StorageKeys.BILI_FULL_COOKIE)
        if (full.isNotEmpty()) full else "SESSDATA=$sessdata; bili_jct=$biliJct; DedeUserID=$dede"
    }

    /** 自动校准屏幕可见的UP主：每0.2秒检查，5秒冷却。先查询真实状态，不一致才修正 */
    fun checkVisibleFollowStatus(mids: List<Long>) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val zombieMap = _zombieFollowings.value.associateBy { it.first.mid }
            var processed = 0
            var fixed = 0

            addDebugLog("开始校准 ${mids.size} 个UP主...")

            for (mid in mids) {
                val pair = zombieMap[mid] ?: continue
                val (user, ts) = pair

                val lastCheck = _followStatusCooldown[mid] ?: 0
                if (now - lastCheck < 5000) {
                    addDebugLog("⏳ ${user.uname} (mid=$mid) 冷却中，跳过")
                    continue
                }

                _followStatusCooldown[mid] = now
                processed++
                addDebugLog("🔍 查询 ${user.uname} (mid=$mid, 本地=${if(user.attribute>=2)"已关注" else "未关注"})...")

                getRelationStatus(user.mid).fold(
                    onSuccess = { realAttr ->
                        val currentlyShown = user.attribute >= 2
                        val actuallyFollowing = realAttr >= 2

                        if (currentlyShown != actuallyFollowing) {
                            val updated = user.copy(attribute = if (actuallyFollowing) 2 else 0)
                            _zombieFollowings.value = _zombieFollowings.value.map {
                                if (it.first.mid == mid) updated to ts else it
                            }
                            saveZombieData()
                            fixed++
                            val from = if (currentlyShown) "已关注" else "未关注"
                            val to = if (actuallyFollowing) "已关注" else "未关注"
                            addDebugLog("✅ ${user.uname}: $from → $to（已修正）")
                        } else {
                            val status = if (actuallyFollowing) "已关注" else "未关注"
                            addDebugLog("✅ ${user.uname}: $status（一致，跳过）")
                        }
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "未知错误"
                        if (msg.startsWith("HTML_BLOCK\n")) {
                            val html = msg.removePrefix("HTML_BLOCK\n")
                            val path = saveHtmlToFile(html, user.mid)
                            addDebugLog("❌ ${user.uname}: 被风控，HTML已保存: $path")
                        } else {
                            addDebugLog("❌ ${user.uname}: 查询失败 ($msg)")
                        }
                    }
                )

                delay(200)
            }

            addDebugLog("校准完成：处理了 $processed 个，修正了 $fixed 个")
        }
    }

    // 视图切换：true=显示僵尸榜, false=显示普通列表
    private val _showZombieView = MutableStateFlow(false)
    val showZombieView: StateFlow<Boolean> = _showZombieView.asStateFlow()

    /** 切换僵尸榜/普通列表视图（不清空数据） */
    fun toggleZombieView() {
        _showZombieView.value = !_showZombieView.value
        saveZombieData()
    }

    fun clearZombieResults() {
        _zombieFollowings.value = emptyList()
        _zombieFollowers.value = emptyList()
        _searchType.value = 0
        _showZombieView.value = false
        _followingSearchPage.value = 1
        _followingSearchHasMore.value = true
        _followerSearchPage.value = 1
        _followerSearchHasMore.value = true
        // 同时清空本地存储和断点
        viewModelScope.launch {
            storage.putString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWINGS, "")
            storage.putString(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWERS, "")
            storage.putInt(com.yourapp.data.StorageKeys.SHOW_ZOMBIE_VIEW, 0)
            // 清除断点
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_PAGE, 1)
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWING_LAST_INDEX, -1)
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_PAGE, 1)
            storage.putInt(com.yourapp.data.StorageKeys.ZOMBIE_FOLLOWER_LAST_INDEX, -1)
        }
    }

    /** 切换关注/取关 */
    fun toggleFollow(user: BiliUser, isFollowing: Boolean) {
        viewModelScope.launch {
            // 从内存列表重新确认当前真实状态，避免UI竞态导致act错误
            val currentInZombie = _zombieFollowings.value.find { it.first.mid == user.mid }?.first
            val currentInFollowing = _followings.value.find { it.mid == user.mid }
            val currentInFollower = _followers.value.find { it.mid == user.mid }
            val current = currentInZombie ?: currentInFollowing ?: currentInFollower ?: user
            val actuallyFollowing = current.attribute >= 2

            val act = if (actuallyFollowing) 2 else 1
            modifyRelation(user.mid, act).fold(
                onSuccess = {
                    val updatedUser = user.copy(attribute = if (actuallyFollowing) 0 else 2)
                    if (actuallyFollowing) {
                        // 取关：只改attribute，保留在列表中
                        _zombieFollowings.value = _zombieFollowings.value.map {
                            if (it.first.mid == user.mid) updatedUser to it.second else it
                        }
                        _followings.value = _followings.value.map {
                            if (it.mid == user.mid) updatedUser else it
                        }
                        _followers.value = _followers.value.map {
                            if (it.mid == user.mid) updatedUser else it
                        }
                    } else {
                        // 关注：更新attribute
                        _followers.value = _followers.value.map { if (it.mid == user.mid) updatedUser else it }
                        _zombieFollowers.value = _zombieFollowers.value.map { if (it.mid == user.mid) updatedUser else it }
                        _zombieFollowings.value = _zombieFollowings.value.map {
                            if (it.first.mid == user.mid) updatedUser to it.second else it
                        }
                    }
                    saveZombieData()
                    _error.value = if (actuallyFollowing) "已取关 ${user.uname}" else "已关注 ${user.uname}"
                },
                onFailure = { err ->
                    val msg = err.message ?: ""
                    // API报"已经关注"但UI显示未关注：回写为已关注
                    if (msg.contains("已经关注") || msg.contains("22014")) {
                        val updated = user.copy(attribute = 2)
                        _zombieFollowings.value = _zombieFollowings.value.map {
                            if (it.first.mid == user.mid) updated to it.second else it
                        }
                        _followings.value = _followings.value.map { if (it.mid == user.mid) updated else it }
                        _followers.value = _followers.value.map { if (it.mid == user.mid) updated else it }
                        saveZombieData()
                        _error.value = "${user.uname} 已关注"
                        return@fold
                    }
                    // API报"未关注"但UI显示已关注：回写为未关注
                    if (msg.contains("未关注") || msg.contains("22013")) {
                        val updated = user.copy(attribute = 0)
                        _zombieFollowings.value = _zombieFollowings.value.map {
                            if (it.first.mid == user.mid) updated to it.second else it
                        }
                        _followings.value = _followings.value.map { if (it.mid == user.mid) updated else it }
                        _followers.value = _followers.value.map { if (it.mid == user.mid) updated else it }
                        saveZombieData()
                        _error.value = "${user.uname} 已取关"
                        return@fold
                    }
                    _error.value = "操作失败: $msg"
                }
            )
        }
    }

    /** 僵尸榜中是否有已取关但未清理的UP主 */
    fun hasUnfollowedInZombieList(): Boolean {
        return _zombieFollowings.value.any { it.first.attribute == 0 }
    }

    /** 清理僵尸榜中已取关的UP主 */
    fun clearUnfollowedFromZombieList() {
        _zombieFollowings.value = _zombieFollowings.value.filter { it.first.attribute != 0 }
        saveZombieData()
    }

    fun logout() {
        viewModelScope.launch {
            _isSearchingFollowings.value = false
            _isSearchingFollowers.value = false
            logoutUseCase()
            _user.value = null
            _followings.value = emptyList()
            _followers.value = emptyList()
            _followingPage.value = 1
            _followerPage.value = 1
            _hasMoreFollowings.value = true
            _hasMoreFollowers.value = true
            _isLoggedIn.value = false
            // 注意：不清空僵尸榜数据和视图状态，退出登录后数据保留
        }
    }

    fun clearError() {
        _error.value = null
    }
}

/** 判断是否默认用户名（bili_xxxxx 格式） */
fun isDefaultUsername(uname: String): Boolean {
    return uname.startsWith("bili_") || uname.matches(Regex("^\\d+\$"))
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
