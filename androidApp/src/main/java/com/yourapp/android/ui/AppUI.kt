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
import androidx.compose.material.icons.filled.MoreVert
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

import com.yourapp.android.service.ZombieSearchService
import com.yourapp.android.util.CrashLogCollector
import com.yourapp.android.ui.WebViewLoginActivity

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
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(factory = factory)
    val isLoggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    // 检查上次崩溃日志
    val activity = context as? android.app.Activity
    val hasCrashLog = remember { activity?.intent?.getBooleanExtra("has_crash_log", false) ?: false }
    val crashLogContent = remember { activity?.intent?.getStringExtra("last_crash_log") ?: "" }
    var showCrashLogDialog by remember { mutableStateOf(hasCrashLog) }

    // 崩溃日志显示对话框
    if (showCrashLogDialog && crashLogContent.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showCrashLogDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("上次崩溃记录")
                }
            },
            text = {
                Column {
                    Text(
                        "应用上次运行时发生了崩溃，以下是崩溃信息：",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            crashLogContent,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 复制到剪贴板按钮
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("崩溃日志", crashLogContent)
                            clipboard.setPrimaryClip(clip)
                            // 显示提示
                            android.widget.Toast.makeText(context, "崩溃日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("📋 复制")
                    }
                    // 打开日志目录按钮
                    TextButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        java.io.File(context.filesDir, "bili_crash_logs.json")
                                    ),
                                    "application/json"
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "无法打开日志文件", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("📂 打开")
                    }
                    // 清除日志按钮
                    Button(
                        onClick = { 
                            CrashLogCollector.clearLogs(context)
                            showCrashLogDialog = false 
                        }
                    ) {
                        Text("清除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashLogDialog = false }) {
                    Text("关闭")
                }
            }
        )
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
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 错误提示
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(error!!)
            vm.clearError()
        }
    }

    // 登录完成提示（使用独立状态避免覆盖错误提示）
    val followingSearchCompleted by vm.followingSearchCompleted.collectAsStateWithLifecycle()
    val followerSearchCompleted by vm.followerSearchCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(followingSearchCompleted, followerSearchCompleted) {
        if (followingSearchCompleted) {
            snackbarHostState.showSnackbar("搜寻僵尸UP已完成！")
            vm.clearFollowingSearchCompleted()
        }
        if (followerSearchCompleted) {
            snackbarHostState.showSnackbar("搜寻僵尸粉已完成！")
            vm.clearFollowerSearchCompleted()
        }
    }

    // 应用生命周期监听（进入前台时自动刷新登录状态）
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

    // 接收 Service 广播（搜索进度）
    val serviceProgress by ZombieSearchService.serviceProgress.collectAsStateWithLifecycle()
    val serviceEta by ZombieSearchService.serviceEta.collectAsStateWithLifecycle()
    val serviceRunning by ZombieSearchService.serviceRunning.collectAsStateWithLifecycle()

    // 定期从本地刷新数据（Service 后台保存后）
    LaunchedEffect(serviceRunning) {
        if (!serviceRunning) {
            vm.reloadZombieData()
        }
    }

    // 定期刷新数据（每 5 秒）
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (!serviceRunning) {
                vm.reloadZombieData()
            }
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
        if (!isLoggedIn) {
            LoginScreen(
                vm = vm,
                onWebViewLogin = {
                    val intent = Intent(context, com.yourapp.android.ui.WebViewLoginActivity::class.java)
                    loginLauncher.launch(intent)
                }
            )
        } else {
            MainContent(
                vm = vm,
                padding = padding,
                hasNotificationPermission = true, // 不需要通知权限
                serviceProgress = serviceProgress,
                serviceEta = serviceEta,
                serviceRunning = serviceRunning
            )
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
fun MainContent(
    vm: AppViewModel,
    padding: PaddingValues,
    hasNotificationPermission: Boolean,
    serviceProgress: String,
    serviceEta: String,
    serviceRunning: Boolean
) {
    val context = LocalContext.current
    val followings by vm.followings.collectAsStateWithLifecycle()
    val followers by vm.followers.collectAsStateWithLifecycle()
    val hasMoreFollowings by vm.hasMoreFollowings.collectAsStateWithLifecycle()
    val hasMoreFollowers by vm.hasMoreFollowers.collectAsStateWithLifecycle()
    val zombieFollowings by vm.zombieFollowings.collectAsStateWithLifecycle()
    val zombieFollowers by vm.zombieFollowers.collectAsStateWithLifecycle()
    val isSearchingFollowings by vm.isSearchingFollowings.collectAsStateWithLifecycle()
    val isSearchingFollowers by vm.isSearchingFollowers.collectAsStateWithLifecycle()
    val showZombieFollowingView by vm.showZombieFollowingView.collectAsStateWithLifecycle()
    val showZombieFollowerView by vm.showZombieFollowerView.collectAsStateWithLifecycle()
    val isRecheckingFailed by vm.isRecheckingFailed.collectAsStateWithLifecycle()
    val recheckProgress by vm.recheckProgress.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }

    // 定期刷新数据
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            vm.reloadZombieData()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Column(Modifier.fillMaxSize()) {
            // 通知权限提示
            if (false) { // 注释掉通知权限提示
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "打开通知后可以查看工作进度哟～",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, "com.yourapp.android")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text("去开启")
                        }
                    }
                }
            }

            // Tab 切换
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 关注 Tab 文本
                val followingTabText = when {
                    showZombieFollowingView -> "僵尸UP⇄"
                    !showZombieFollowingView && (zombieFollowings.isNotEmpty() || isSearchingFollowings) -> "关注⇄"
                    else -> "关注"
                }
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        if (selectedTab == 0) {
                            vm.toggleZombieFollowingView()
                        } else {
                            selectedTab = 0
                        }
                    },
                    text = { Text(followingTabText) }
                )

                // 粉丝 Tab 文本
                val followerTabText = when {
                    showZombieFollowerView -> "僵尸粉⇄"
                    !showZombieFollowerView && (zombieFollowers.isNotEmpty() || isSearchingFollowers) -> "粉丝⇄"
                    else -> "粉丝"
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        if (selectedTab == 1) {
                            vm.toggleZombieFollowerView()
                        } else {
                            selectedTab = 1
                        }
                    },
                    text = { Text(followerTabText) }
                )
            }

            // 当前Tab搜索进度 + 预估时间
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (currentEta.isNotEmpty()) {
                Text(
                    currentEta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 调试功能状态
            val enabledFeatures by vm.debugFeatures.collectAsStateWithLifecycle()
            val showUid = DebugFeature.SHOW_UID in enabledFeatures
            if (isRecheckingFailed) {
                Text(
                    recheckProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 列表内容
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    0 -> when {
                        showZombieFollowingView && zombieFollowings.isNotEmpty() -> {
                            ZombieFollowingList(
                                users = zombieFollowings,
                                vm = vm,
                                showUid = showUid,
                                onNameClick = { mid -> vm.onUpNameClicked(mid) }
                            )
                        }
                        showZombieFollowingView && zombieFollowings.isEmpty() && !isSearchingFollowings -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无僵尸UP数据，点击搜索按钮开始")
                            }
                        }
                        else -> UserList(
                            users = followings,
                            hasMore = hasMoreFollowings,
                            showUid = showUid,
                            onLoadMore = { vm.loadFollowings(loadMore = true) },
                            onToggleFollow = { user, isFollowing ->
                                vm.toggleFollow(user, isFollowing)
                            },
                            onNameClick = { mid -> vm.onUpNameClicked(mid) }
                        )
                    }
                    1 -> when {
                        showZombieFollowerView && zombieFollowers.isNotEmpty() -> {
                            ZombieFollowerList(
                                users = zombieFollowers,
                                vm = vm,
                                showUid = showUid
                            )
                        }
                        showZombieFollowerView && zombieFollowers.isEmpty() && !isSearchingFollowers -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("暂无僵尸粉数据，点击搜索按钮开始")
                            }
                        }
                        else -> UserList(
                            users = followers,
                            hasMore = hasMoreFollowers,
                            showUid = showUid,
                            onLoadMore = { vm.loadFollowers(loadMore = true) },
                            onToggleFollow = { user, isFollowing ->
                                vm.toggleFollow(user, isFollowing)
                            },
                            onNameClick = { mid -> vm.onUpNameClicked(mid) },
                            onRemoveFollower = { mid -> vm.removeFollower(mid) }
                        )
                    }
                }
            }
        }

        // 悬浮按钮组（右下角）- 搜索/刷新 + 调试，一起拖拽
        val showDebug by vm.showDebugOverlay.collectAsStateWithLifecycle()
        val debugLogs by vm.debugLogs.collectAsStateWithLifecycle()
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val isCurrentSearching = when (selectedTab) {
            0 -> isSearchingFollowings
            1 -> isSearchingFollowers
            else -> false
        }

        val buttonText = when {
            isCurrentSearching && selectedTab == 0 -> "停止搜寻僵尸UP"
            isCurrentSearching && selectedTab == 1 -> "停止搜寻僵尸粉"
            selectedTab == 0 -> if (zombieFollowings.isNotEmpty()) "继续搜寻僵尸UP" else "搜寻僵尸UP"
            selectedTab == 1 -> if (zombieFollowers.isNotEmpty()) "继续搜寻僵尸粉" else "搜寻僵尸粉"
            else -> ""
        }

        Column(
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
                .zIndex(999f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 搜索/刷新按钮（在调试按钮上方）
            if (isCurrentSearching) {
                // 搜索中：停止按钮
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> vm.stopFollowingSearch()
                            1 -> vm.stopFollowerSearch()
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                // 未搜索：搜索按钮 + 刷新按钮
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> vm.startZombieFollowingSearch(continueFromLast = buttonText == "继续搜寻僵尸UP")
                            1 -> vm.startZombieFollowerSearch(continueFromLast = buttonText == "继续搜寻僵尸粉")
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> {
                                vm.clearZombieFollowings()
                                vm.startZombieFollowingSearch(continueFromLast = false)
                            }
                            1 -> {
                                vm.clearZombieFollowers()
                                vm.startZombieFollowerSearch(continueFromLast = false)
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 调试按钮（在最下方）
            if (BuildConfig.DEBUG) {
                FloatingActionButton(
                    onClick = { vm.toggleDebugOverlay() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Text("🐛", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // 调试日志面板
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
                    // 设置按钮
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

                // 功能按钮网格
                val enabledFeatures by vm.debugFeatures.collectAsStateWithLifecycle()
                if (enabledFeatures.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val featureButtons = mutableListOf<@Composable () -> Unit>()

                    if (DebugFeature.BATCH_CALIBRATE in enabledFeatures) {
                        val hasZombieUp = showZombieFollowingView && selectedTab == 0 && zombieFollowings.isNotEmpty()
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
                                    enabled = !isRecheckingFailed,
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
                        }
                    }

                    if (DebugFeature.CALIBRATE_VISIBLE in enabledFeatures) {
                        featureButtons.add {
                            AssistChip(
                                onClick = { vm.calibrateVisible() },
                                enabled = !isRecheckingFailed,
                                label = { Text("👁 校准可见", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

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

                    if (DebugFeature.CLEAR_ZOMBIE in enabledFeatures) {
                        featureButtons.add {
                            AssistChip(
                                onClick = { vm.clearZombieFollowings() },
                                label = { Text("🗑 清僵尸UP", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    if (DebugFeature.CLEAR_ZOMBIE_FOLLOWERS in enabledFeatures) {
                        featureButtons.add {
                            AssistChip(
                                onClick = { vm.clearZombieFollowers() },
                                label = { Text("🗑 清僵尸粉", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }

                    // 流式布局渲染
                    if (featureButtons.isNotEmpty()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val maxWidth = maxWidth
                            val estimatedChipWidth = 100.dp
                            val chipsPerRow = (maxWidth / estimatedChipWidth).toInt().coerceAtLeast(1)

                            val allRows = featureButtons.chunked(chipsPerRow)
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                // 日志列表
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

                // 拖拽手柄
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

        // WebView 校准
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

@Composable
fun UserCard(user: BiliUser, showUid: Boolean = false) {
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
                Text(
                    text = user.uname,
                    style = MaterialTheme.typography.titleMedium
                )
                if (showUid) {
                    Text(
                        text = "ID: ${user.mid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
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
    showUid: Boolean = false,
    onLoadMore: () -> Unit,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {},
    onRemoveFollower: ((Long) -> Unit)? = null
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
            UserListItem(user, showUid = showUid, onToggleFollow = onToggleFollow, onNameClick = onNameClick, onRemoveFollower = onRemoveFollower)
        }
    }
}

@Composable
fun UserListItem(
    user: BiliUser,
    showUid: Boolean = false,
    onToggleFollow: (BiliUser, Boolean) -> Unit = { _, _ -> },
    onNameClick: (Long) -> Unit = {},
    onRemoveFollower: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val isFollowing = user.attribute >= 2
    val isFollowerScene = onRemoveFollower != null
    val status = when (user.attribute) {
        0 -> if (isFollowerScene) "回关" to MaterialTheme.colorScheme.primary else "未关注" to MaterialTheme.colorScheme.error
        2 -> if (isFollowerScene) "已回关" to MaterialTheme.colorScheme.primary else "已关注" to MaterialTheme.colorScheme.primary
        6 -> "互相关注" to MaterialTheme.colorScheme.tertiary
        else -> "未知" to MaterialTheme.colorScheme.onSurface
    }
    val btnColor: androidx.compose.ui.graphics.Color = if (isFollowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable {
                onNameClick(user.mid)
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.uname,
                    style = MaterialTheme.typography.titleMedium
                )
                if (showUid) {
                    Text(
                        text = "ID: ${user.mid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (user.sign.isNotBlank()) {
                    Text(user.sign, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onToggleFollow(user, isFollowing) },
                    colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
                ) {
                    Text(status.first)
                }
                // 粉丝列表场景显示 ⋮ 菜单
                if (onRemoveFollower != null) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    
    // 底部弹出菜单 - 用 AlertDialog 替代 ModalBottomSheet
    if (showMenu && onRemoveFollower != null) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(user.uname) },
            text = { Text("确定要移除这个粉丝吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFollower(user.mid)
                        showMenu = false
                    }
                ) {
                    Text("移除粉丝", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMenu = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ZombieFollowingList(
    users: List<Pair<BiliUser, Long>>,
    vm: AppViewModel,
    showUid: Boolean = false,
    onNameClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val followStatusOverrides by vm.followStatusOverrides.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(users, key = { it.first.mid }) { (user, timestamp) ->
            val overrideStatus = followStatusOverrides[user.mid]
            val isFollowing: Boolean = overrideStatus?.let { it >= 2 } ?: (user.attribute >= 2)
            val status: Pair<String, androidx.compose.ui.graphics.Color> = when {
                overrideStatus == 0 -> "未关注" to MaterialTheme.colorScheme.error
                overrideStatus == 2 -> "已关注" to MaterialTheme.colorScheme.primary
                overrideStatus == 6 -> "互相关注" to MaterialTheme.colorScheme.tertiary
                user.attribute == 0 -> "未关注" to MaterialTheme.colorScheme.error
                user.attribute == 2 -> "已关注" to MaterialTheme.colorScheme.primary
                user.attribute == 6 -> "互相关注" to MaterialTheme.colorScheme.tertiary
                else -> "未知" to MaterialTheme.colorScheme.onSurface
            }
            val btnColor: androidx.compose.ui.graphics.Color = if (isFollowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
            val daysAgo = if (timestamp > 0) {
                val days = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60 * 24)
                "${days}天前"
            } else "无法获取"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable {
                        onNameClick(user.mid)
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showUid) "${user.uname}  ${user.mid}" else user.uname,
                            style = if (showUid) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
                        )
                        Text("最后更新: $date ($daysAgo)", style = MaterialTheme.typography.bodySmall)
                        if (user.sign.isNotBlank()) {
                            Text(user.sign, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = { vm.toggleFollow(user, isFollowing) },
                        colors = ButtonDefaults.textButtonColors(contentColor = btnColor)
                    ) {
                        Text(status.first)
                    }
                }
            }
        }
    }
}

@Composable
fun ZombieFollowerList(
    users: List<BiliUser>,
    vm: AppViewModel,
    showUid: Boolean = false
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(users, key = { it.mid }) { user ->
            val isDefault = isDefaultUsername(user.uname)
            var showMenu by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.bilibili.com/space/${user.mid}"))
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showUid) "${user.uname}  ${user.mid}" else user.uname,
                            style = if (showUid) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
                        )
                        if (isDefault) {
                            Text(
                                "疑似小号/默认账号",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { vm.toggleFollow(user, user.attribute >= 2) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("回关")
                        }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // 弹出菜单 - 用 AlertDialog 替代
            if (showMenu) {
                AlertDialog(
                    onDismissRequest = { showMenu = false },
                    title = { Text(user.uname) },
                    text = { Text("确定要移除这个粉丝吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.removeFollower(user.mid)
                                showMenu = false
                            }
                        ) {
                            Text("移除粉丝", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showMenu = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}