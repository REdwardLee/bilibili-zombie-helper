package com.yourapp.android.ui

import com.yourapp.android.BuildConfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.android.di.AppViewModel
import com.yourapp.android.di.AppViewModelFactory
import com.yourapp.android.di.isDefaultUsername
import com.yourapp.domain.BiliUser
import kotlinx.coroutines.delay

import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUI(
    factory: AppViewModelFactory
) {
    val vm: AppViewModel = viewModel(factory = factory)
    val isLoggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 动态轮询通知权限（每秒检测一次，直到授权后自动消失）
    var hasNotificationPermission by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            hasNotificationPermission = nm.areNotificationsEnabled()
            delay(1000)
        }
    }

    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == androidx.activity.ComponentActivity.RESULT_OK) {
            vm.refreshLoginStatus()
        }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    var selectedTab by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarEvent by vm.snackbarEvent.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        val err = error
        if (err != null) {
            // 后出来的提示立即覆盖当前显示的
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = err,
                duration = SnackbarDuration.Short
            )
            vm.clearError()
        }
    }

    LaunchedEffect(snackbarEvent) {
        val evt = snackbarEvent
        if (evt != null) {
            // 后出来的提示立即覆盖当前显示的（不再区分优先级）
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = evt.message,
                actionLabel = evt.actionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed && evt.userMid != null) {
                vm.setSpecialFollowFromSnackbar(evt.userMid)
            }
            vm.clearSnackbarEvent()
        }
    }

    // 搜索完成提示（仅显示一次）
    val followingSearchCompleted by vm.followingSearchCompleted.collectAsStateWithLifecycle()
    val followerSearchCompleted by vm.followerSearchCompleted.collectAsStateWithLifecycle()

    LaunchedEffect(followingSearchCompleted) {
        if (followingSearchCompleted) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "搜寻僵尸UP已完成",
                duration = SnackbarDuration.Short
            )
            vm.clearFollowingSearchCompleted()
        }
    }

    LaunchedEffect(followerSearchCompleted) {
        if (followerSearchCompleted) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "搜寻僵尸粉已完成",
                duration = SnackbarDuration.Short
            )
            vm.clearFollowerSearchCompleted()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B站助手") },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "退出登录")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            if (!isLoggedIn) {
                LoginScreen(
                    vm = vm,
                    onWebViewLogin = {
                        loginLauncher.launch(Intent(context, WebViewLoginActivity::class.java))
                    }
                )
            } else {
                MainScreen(
                    vm = vm,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    hasNotificationPermission = hasNotificationPermission,
                    onOpenNotificationSettings = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, onWebViewLogin: () -> Unit) {
    var cookieInput by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onWebViewLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bilibili账号登录")
        }
    }
}

@Composable
fun MainScreen(
    vm: AppViewModel,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    hasNotificationPermission: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val followings by vm.followings.collectAsStateWithLifecycle()
    val followers by vm.followers.collectAsStateWithLifecycle()
    val zombieFollowings by vm.zombieFollowings.collectAsStateWithLifecycle()
    val zombieFollowers by vm.zombieFollowers.collectAsStateWithLifecycle()
    val isSearchingFollowings by vm.isSearchingFollowings.collectAsStateWithLifecycle()
    val isSearchingFollowers by vm.isSearchingFollowers.collectAsStateWithLifecycle()
    val showZombieFollowingView by vm.showZombieFollowingView.collectAsStateWithLifecycle()
    val showZombieFollowerView by vm.showZombieFollowerView.collectAsStateWithLifecycle()
    
    // 当前Tab对应的僵尸视图状态
    val showZombieView = when (selectedTab) {
        0 -> showZombieFollowingView
        1 -> showZombieFollowerView
        else -> false
    }
    val followingProgress by vm.followingSearchProgress.collectAsStateWithLifecycle()
    val followerProgress by vm.followerSearchProgress.collectAsStateWithLifecycle()
    val hasMoreFollowings by vm.hasMoreFollowings.collectAsStateWithLifecycle()
    val hasMoreFollowers by vm.hasMoreFollowers.collectAsStateWithLifecycle()
    val followingSearchCompleted by vm.followingSearchCompleted.collectAsStateWithLifecycle()
    val followerSearchCompleted by vm.followerSearchCompleted.collectAsStateWithLifecycle()

    // 监听后台 Service 状态
    val serviceRunning by com.yourapp.android.service.ZombieSearchService.serviceRunning.collectAsState()
    val serviceProgress by com.yourapp.android.service.ZombieSearchService.serviceProgress.collectAsState()
    val serviceEta by com.yourapp.android.service.ZombieSearchService.serviceEta.collectAsState()

    // 定期从本地刷新数据（Service 后台保存后）
    LaunchedEffect(serviceRunning) {
        while (serviceRunning) {
            vm.reloadZombieData()
            delay(2000)
        }
    }

    // 同步 Service 搜索状态到 ViewModel
    LaunchedEffect(serviceRunning) {
        if (!serviceRunning) {
            // 区分用户手动暂停和 Service 自动完成
            if (isSearchingFollowings) vm.onFollowingSearchCompleted()
            if (isSearchingFollowers) vm.onFollowerSearchCompleted()
        }
    }

    // 当前Tab是否正在搜索
    val isCurrentSearching = when (selectedTab) {
        0 -> isSearchingFollowings
        1 -> isSearchingFollowers
        else -> false
    }

    // 当前Tab是否有僵尸搜索结果
    val hasZombieResult = when (selectedTab) {
        0 -> zombieFollowings.isNotEmpty()
        1 -> zombieFollowers.isNotEmpty()
        else -> false
    }

    // 按钮文本
    val buttonText = when {
        isCurrentSearching && selectedTab == 0 -> "停止搜寻僵尸UP"
        isCurrentSearching && selectedTab == 1 -> "停止搜寻僵尸粉"
        selectedTab == 0 -> if (hasZombieResult) "继续搜寻僵尸UP" else "搜寻僵尸UP"
        else -> if (hasZombieResult) "继续搜寻僵尸粉" else "搜寻僵尸粉"
    }

    // 列表标题（各自独立，不互相覆盖）
    val listTitle = when (selectedTab) {
        0 -> when {
            isSearchingFollowings -> "UP僵尸榜"
            showZombieView && zombieFollowings.isNotEmpty() -> "UP僵尸榜"
            else -> "关注列表"
        }
        1 -> when {
            isSearchingFollowers -> "粉丝僵尸榜"
            showZombieView && zombieFollowers.isNotEmpty() -> "粉丝僵尸榜"
            else -> "粉丝列表"
        }
        else -> ""
    }

    // 排查进度状态（全局共用，清理前校验和排查失败UP都用）
    val isRechecking by vm.isRecheckingFailed.collectAsStateWithLifecycle()
    val recheckProgress by vm.recheckProgress.collectAsStateWithLifecycle()

    // 搜索进度文本
    val progressText = when (selectedTab) {
        0 -> followingProgress
        1 -> followerProgress
        else -> ""
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 通知权限缺失提示（可关闭）
        var dismissNotificationWarning by remember { mutableStateOf(false) }
        if (!hasNotificationPermission && !dismissNotificationWarning) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "打开通知后可以查看工作进度哟～",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    TextButton(onClick = onOpenNotificationSettings) {
                        Text("去开启", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { dismissNotificationWarning = true }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }
        }

            if (user != null) {
                UserCard(user!!)
                Spacer(Modifier.height(8.dp))
            }

            // Tab 切换 + 僵尸视图切换 + 搜索按钮
            var showClearDialog by remember { mutableStateOf(false) }
            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("清空数据？") },
                    text = { Text("确认清空当前Tab的搜索结果吗？") },
                    confirmButton = {
                        Button(onClick = {
                            vm.clearZombieResults()
                            showClearDialog = false
                        }) {
                            Text("清空")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // 搜索控制行（按钮 + 刷新）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            isCurrentSearching && selectedTab == 0 -> vm.stopFollowingSearch()
                            isCurrentSearching && selectedTab == 1 -> vm.stopFollowerSearch()
                            selectedTab == 0 -> vm.startZombieFollowingSearch(continueFromLast = buttonText == "继续搜寻僵尸UP")
                            selectedTab == 1 -> vm.startZombieFollowerSearch(continueFromLast = buttonText == "继续搜寻僵尸粉")
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isCurrentSearching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(buttonText)
                }

                Spacer(Modifier.width(8.dp))

                // 圆形刷新按钮
                FilledIconButton(
                    onClick = {
                        vm.refreshCurrentList(selectedTab, showZombieView)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新"
                    )
                }
            }

            // 当前Tab搜索进度 + 预估时间（按钮下方显示，直接从Service读取）
            val currentProgress = when (selectedTab) {
                0 -> if (isSearchingFollowings) serviceProgress else ""
                1 -> if (isSearchingFollowers) serviceProgress else ""
                else -> ""
            }
            val currentEta = when (selectedTab) {
                0 -> if (isSearchingFollowings) serviceEta else ""
                1 -> ""
                else -> ""
            }
            if (currentProgress.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    currentProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (currentEta.isNotEmpty()) {
                    Text(
                        currentEta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 排查进度条（全局共用）
            if (isRechecking) {
                Spacer(Modifier.height(4.dp))
                Text(
                    recheckProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))

            // Tab 切换（带僵尸视图切换功能，⇄表示可切换）
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 关注 Tab 文本：只根据关注侧自身状态显示，不受当前选中Tab影响
                val followingTabText = when {
                    showZombieFollowingView -> "僵尸UP⇄"  // 关注侧在僵尸视图
                    !showZombieFollowingView && (zombieFollowings.isNotEmpty() || isSearchingFollowings) -> "关注⇄"  // 关注侧在普通视图，有僵尸数据
                    else -> "关注"  // 无僵尸数据
                }
                Tab(
                    selected = selectedTab == 0,
                    onClick = { 
                        if (selectedTab == 0 && (zombieFollowings.isNotEmpty() || isSearchingFollowings)) {
                            // 已在关注页且有僵尸数据，点击切换关注侧视图
                            vm.toggleZombieFollowingView()
                        } else {
                            onTabChange(0)
                        }
                    },
                    text = { Text(followingTabText) }
                )
                
                // 粉丝 Tab 文本：只根据粉丝侧自身状态显示，不受当前选中Tab影响
                val followerTabText = when {
                    showZombieFollowerView -> "僵尸粉⇄"  // 粉丝侧在僵尸视图
                    !showZombieFollowerView && (zombieFollowers.isNotEmpty() || isSearchingFollowers) -> "粉丝⇄"  // 粉丝侧在普通视图，有僵尸数据
                    else -> "粉丝"  // 无僵尸数据
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { 
                        if (selectedTab == 1 && (zombieFollowers.isNotEmpty() || isSearchingFollowers)) {
                            // 已在粉丝页且有僵尸数据，点击切换粉丝侧视图
                            vm.toggleZombieFollowerView()
                        } else {
                            onTabChange(1)
                        }
                    },
                    text = { Text(followerTabText) }
                )
            }

            // 列表内容（weight(1f) 撑满剩余空间）
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    0 -> when {
                        showZombieView && zombieFollowings.isNotEmpty() -> {
                            ZombieFollowingList(
                                users = zombieFollowings,
                                vm = vm,
                                onToggleFollow = { user, isFollowing ->
                                    vm.toggleFollow(user, isFollowing)
                                }
                            )
                        }
                        showZombieView && zombieFollowings.isEmpty() && !isSearchingFollowings -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无僵尸UP数据", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        else -> UserList(
                            users = followings,
                            hasMore = hasMoreFollowings,
                            onLoadMore = { vm.loadFollowings(loadMore = true) },
                            onToggleFollow = { user, isFollowing ->
                                vm.toggleFollow(user, isFollowing)
                            },
                            onNameClick = { mid -> vm.onUpNameClicked(mid) }
                        )
                    }
                    1 -> when {
                        showZombieView && zombieFollowers.isNotEmpty() -> {
                            ZombieFollowerList(
                                users = zombieFollowers,
                                onToggleFollow = { user, isFollowing ->
                                    vm.toggleFollow(user, isFollowing)
                                },
                                onNameClick = { mid -> vm.onUpNameClicked(mid) }
                            )
                        }
                        showZombieView && zombieFollowers.isEmpty() && !isSearchingFollowers -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无僵尸粉数据", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        else -> UserList(
                            users = followers,
                            hasMore = hasMoreFollowers,
                            onLoadMore = { vm.loadFollowers(loadMore = true) },
                            onToggleFollow = { user, isFollowing ->
                                vm.toggleFollow(user, isFollowing)
                            },
                            onNameClick = { mid -> vm.onUpNameClicked(mid) }
                        )
                    }
                }
            }
        }

        // 悬浮调试按钮
        val showDebug by vm.showDebugOverlay.collectAsStateWithLifecycle()
        val debugLogs by vm.debugLogs.collectAsStateWithLifecycle()
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        if (BuildConfig.DEBUG) {
            // 调试按钮在最上层
            FloatingActionButton(
                onClick = { vm.toggleDebugOverlay() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .zIndex(999f)
            ) {
                Text("🐛", style = MaterialTheme.typography.titleMedium)
            }

            // 调试日志面板（自动适应大小）
            if (showDebug) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .alpha(0.9f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                        .zIndex(998f)
                ) {
                    // 标题行 + 设置按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "调试 (${debugLogs.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // 设置按钮（最右侧）
                        var showDebugSettings by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDebugSettings = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (showDebugSettings) {
                            DebugFeatureSettingsDialog(
                                onDismiss = { showDebugSettings = false },
                                vm = vm
                            )
                        }
                    }
                    
                    // 功能按钮网格展示（根据设置动态显示）- Chip风格
                    val enabledFeatures by vm.debugFeatures.collectAsStateWithLifecycle()
                    if (enabledFeatures.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        
                        // 收集所有功能按钮
                        val featureButtons = mutableListOf<@Composable () -> Unit>()
                        
                        // 全校准按钮
                        if (DebugFeature.BATCH_CALIBRATE in enabledFeatures) {
                            val hasZombieUp = showZombieView && selectedTab == 0 && zombieFollowings.isNotEmpty()
                            val isBatchCalibrating by vm.isBatchCalibrating.collectAsStateWithLifecycle()
                            val isBatchPaused by vm.isBatchCalibrationPaused.collectAsStateWithLifecycle()
                            var showPauseDialog by remember { mutableStateOf(false) }

                            if (hasZombieUp && !isCurrentSearching) {
                                featureButtons.add {
                                    AssistChip(
                                        onClick = {
                                            if (!isBatchCalibrating) {
                                                vm.batchCalibrateByFollowingList()
                                            } else if (!isBatchPaused) {
                                                vm.pauseBatchCalibration()
                                            } else {
                                                showPauseDialog = true
                                            }
                                        },
                                        enabled = !isRechecking,
                                        label = {
                                            Text(
                                                when {
                                                    !isBatchCalibrating -> "🔧 全校准"
                                                    !isBatchPaused -> "⏸ 暂停"
                                                    else -> "⏸ 已暂停"
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    )
                                }

                                if (showPauseDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showPauseDialog = false },
                                        title = { Text("全校准已暂停") },
                                        text = { Text("选择操作") },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    vm.resumeBatchCalibration()
                                                    showPauseDialog = false
                                                }
                                            ) {
                                                Text("继续")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(
                                                onClick = {
                                                    vm.stopBatchCalibration()
                                                    showPauseDialog = false
                                                }
                                            ) {
                                                Text("停止")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // 校准当前可见按钮
                        if (DebugFeature.CALIBRATE_VISIBLE in enabledFeatures) {
                            featureButtons.add {
                                AssistChip(
                                    onClick = { vm.calibrateVisible() },
                                    enabled = !isRechecking,
                                    label = { Text("👁 校准可见", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 打开日志保存目录
                        if (DebugFeature.OPEN_LOG_DIR in enabledFeatures) {
                            featureButtons.add {
                                AssistChip(
                                    onClick = {
                                        val intent = vm.openHtmlDirectory()
                                        if (intent != null) context.startActivity(intent)
                                    },
                                    label = { Text("📁 日志目录", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 保存日志按钮
                        if (DebugFeature.SAVE_LOGS in enabledFeatures) {
                            featureButtons.add {
                                AssistChip(
                                    onClick = { vm.saveDebugLogsToFile() },
                                    label = { Text("💾 保存日志", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 清除日志按钮
                        if (DebugFeature.CLEAR_LOGS in enabledFeatures) {
                            featureButtons.add {
                                AssistChip(
                                    onClick = { vm.clearDebugLogs() },
                                    label = { Text("📝 清日志", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 清空僵尸UP按钮
                        if (DebugFeature.CLEAR_ZOMBIE in enabledFeatures) {
                            featureButtons.add {
                                AssistChip(
                                    onClick = { vm.clearZombieFollowings() },
                                    label = { Text("🗑 清空僵尸", style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 流式布局渲染（自动适应宽度）
                        if (featureButtons.isNotEmpty()) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val maxWidth = maxWidth
                                
                                // 估算每行能放几个 chip
                                val estimatedChipWidth = 100.dp
                                val chipsPerRow = (maxWidth / estimatedChipWidth).toInt().coerceAtLeast(1)
                                
                                val allRows = featureButtons.chunked(chipsPerRow)
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    allRows.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            row.forEach { chip ->
                                                chip()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // 日志列表（全部显示，可滚动）
                    var panelHeight by remember { mutableFloatStateOf(200f) }
                    val minHeight = 100f
                    val maxHeight = 500f
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minHeight.dp, max = panelHeight.dp)
                    ) {
                        if (debugLogs.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = false
                            ) {
                                items(debugLogs.reversed()) { log ->
                                    Text(
                                        log,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "暂无日志",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    
                    // 拖拽手柄（在底部）- 1:1 跟手拖拽
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDragEnd = { },
                                    onDragCancel = { }
                                ) { change, dragAmount ->
                                    change.consume()
                                    // dragAmount.y 是像素值，转换为 dp
                                    val dragDp = dragAmount.y / density.density
                                    panelHeight = (panelHeight + dragDp).coerceIn(minHeight, maxHeight)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Divider(
                            modifier = Modifier.width(40.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 2.dp
                        )
                    }
                }
            }

            // WebView 校准（新版，修复 cookie/防重/销毁）
            val webViewCurrentMid by vm.webViewCurrentMid.collectAsStateWithLifecycle()
            val webViewProcessing by vm.webViewProcessing.collectAsStateWithLifecycle()
            if (webViewProcessing && webViewCurrentMid != null) {
                val mid = webViewCurrentMid!!
                CalibrationWebView(
                    mid = mid,
                    cookieString = vm.getWebViewCookie(),
                    onResult = { resultMid, isFollowing, isTimeout ->
                        vm.reportWebViewResult(resultMid, isFollowing, isTimeout)
                    },
                    modifier = Modifier.size(1.dp).alpha(0f)
                )
            }
        }
    }
}

@Composable
fun UserCard(user: BiliUser) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(user.uname, style = MaterialTheme.typography.titleMedium)
                Text("UID: ${user.mid} | Lv${user.level}", style = MaterialTheme.typography.bodySmall)
                if (user.sign.isNotBlank()) {
                    Text(user.sign, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun UserList(
    users: List<BiliUser>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {}
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(users, key = { it.mid }) { user ->
            UserListItem(user, onToggleFollow = onToggleFollow, onNameClick = onNameClick)
        }
    }
}

@Composable
fun UserListItem(
    user: BiliUser,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val status = com.yourapp.domain.FollowStatus.fromAttribute(user.attribute, user.special)

    val btnColor = when (status) {
        com.yourapp.domain.FollowStatus.SPECIAL -> MaterialTheme.colorScheme.secondary
        com.yourapp.domain.FollowStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        com.yourapp.domain.FollowStatus.NONE -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.uname,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onNameClick(user.mid)
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                        context.startActivity(intent)
                    }
                )
                Text("UID: ${user.mid}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = { onToggleFollow(user, user.attribute >= 2) },
                colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
            ) {
                Text(status.label)
            }
        }
    }
}

// ========== 僵尸榜列表 ==========

@Composable
fun ZombieFollowingList(
    users: List<Pair<BiliUser, Long>>,
    vm: AppViewModel,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> }
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("点击「搜寻僵尸UP」开始分析", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                users,
                key = { it.first.mid }
            ) { (user, lastUpdate) ->
                ZombieFollowingItem(
                    user = user,
                    lastUpdate = lastUpdate,
                    vm = vm,
                    onToggleFollow = onToggleFollow
                )
            }
        }

        LaunchedEffect(listState, users) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) emptyList()
                else {
                    val first = visible.first().index
                    val last = visible.last().index.coerceAtMost(users.size - 1)
                    users.subList(first, last + 1).map { it.first.mid }
                }
            }.collect { visibleMids ->
                vm.updateVisibleMids(visibleMids)
            }
        }
    }
}

@Composable
fun ZombieFollowingItem(
    user: BiliUser,
    lastUpdate: Long,
    vm: AppViewModel,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }

    // 缓存文本计算，颜色在composable层直接获取
    val (timeText, detailText) = remember(user.mid, lastUpdate) {
        when (lastUpdate) {
            -1L -> Pair("账号被封禁", "")
            -2L -> Pair("无动态", "从未发过动态或动态已清空")
            0L -> Pair("无法获取更新时间", "")
            else -> {
                val diff = System.currentTimeMillis() - lastUpdate
                val days = diff / (1000 * 60 * 60 * 24)
                val relative = when {
                    days > 365 -> "${days / 365}年没更新了 ⚠️"
                    days > 90 -> "${days / 30}个月没更新了 ⚠️"
                    days > 30 -> "${days}天没更新了"
                    days > 7 -> "${days / 7}周没更新了"
                    days > 0 -> "${days}天前更新"
                    else -> "今天更新"
                }
                val exact = "最后更新: ${dateFormat.format(java.util.Date(lastUpdate))}"
                Pair(relative, exact)
            }
        }
    }

    val timeColor = when (lastUpdate) {
        -1L -> MaterialTheme.colorScheme.error
        -2L -> MaterialTheme.colorScheme.onSurfaceVariant
        0L -> MaterialTheme.colorScheme.error
        else -> {
            val diff = System.currentTimeMillis() - lastUpdate
            when {
                diff > 365L * 24 * 60 * 60 * 1000 -> MaterialTheme.colorScheme.error
                diff > 90L * 24 * 60 * 60 * 1000 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        }
    }

    val status = com.yourapp.domain.FollowStatus.fromAttribute(user.attribute, user.special)
    val btnColor = when (status) {
        com.yourapp.domain.FollowStatus.SPECIAL -> MaterialTheme.colorScheme.secondary
        com.yourapp.domain.FollowStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        com.yourapp.domain.FollowStatus.NONE -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.uname,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        vm.onUpNameClicked(user.mid)
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                        context.startActivity(intent)
                    }
                )
                Text(
                    timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = timeColor
                )
                if (detailText.isNotEmpty()) {
                    Text(
                        detailText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = { onToggleFollow(user, user.attribute >= 2) },
                colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
            ) {
                Text(status.label)
            }
        }
    }
}

@Composable
fun ZombieFollowerList(
    users: List<BiliUser>,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {}
) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("点击「搜寻僵尸粉」开始分析", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn {
            items(users, key = { it.mid }) { user ->
                ZombieFollowerItem(user, onToggleFollow = onToggleFollow, onNameClick = onNameClick)
            }
        }
    }
}

@Composable
fun ZombieFollowerItem(
    user: BiliUser,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val isDefault = isDefaultUsername(user.uname)
    val status = com.yourapp.domain.FollowStatus.fromAttribute(user.attribute, user.special)
    val btnColor = when (status) {
        com.yourapp.domain.FollowStatus.SPECIAL -> MaterialTheme.colorScheme.secondary
        com.yourapp.domain.FollowStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        com.yourapp.domain.FollowStatus.NONE -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (isDefault) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else CardDefaults.cardColors()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isDefault) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isDefault) MaterialTheme.colorScheme.onError else LocalContentColor.current
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.uname,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDefault) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onNameClick(user.mid)
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                        context.startActivity(intent)
                    }
                )
                Text("UID: ${user.mid}", style = MaterialTheme.typography.bodySmall)
                if (isDefault) {
                    Text(
                        "疑似小号/默认账号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(
                onClick = { onToggleFollow(user, user.attribute >= 2) },
                colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
            ) {
                Text(status.label)
            }
        }
    }
}
