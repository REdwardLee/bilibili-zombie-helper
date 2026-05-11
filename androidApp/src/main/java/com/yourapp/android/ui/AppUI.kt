package com.yourapp.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.android.di.AppViewModel
import com.yourapp.android.di.AppViewModelFactory
import com.yourapp.domain.BiliUser

import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUI(factory: AppViewModelFactory) {
    val vm: AppViewModel = viewModel(factory = factory)
    val isLoggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 错误提示
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, actionLabel = "确定")
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isLoggedIn) {
                TopAppBar(
                    title = { Text("B站助手") },
                    actions = {
                        IconButton(onClick = { vm.logout() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "退出")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            if (!isLoggedIn) {
                LoginScreen(
                    vm = vm,
                    onWebViewLogin = {
                        context.startActivity(Intent(context, WebViewLoginActivity::class.java))
                    }
                )
            } else {
                MainScreen(vm)
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
        Text("B站助手", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("登录你的B站账号", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        // 主要登录方式：WebView
        Button(
            onClick = onWebViewLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("浏览器登录（推荐）")
        }

        Spacer(Modifier.height(12.dp))

        // 备选方式：手动输入
        TextButton(
            onClick = { showManualInput = !showManualInput }
        ) {
            Text(if (showManualInput) "收起手动输入" else "手动输入Cookie")
        }

        if (showManualInput) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                label = { Text("Cookie 字符串") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 6
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.saveCookieAndLogin(cookieInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = cookieInput.isNotBlank()
            ) {
                Text("登录")
            }
        }
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    val user by vm.user.collectAsStateWithLifecycle()
    val followings by vm.followings.collectAsStateWithLifecycle()
    val followers by vm.followers.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("关注列表", "粉丝列表")

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 用户信息卡片
        user?.let { UserCard(it) }

        Spacer(Modifier.height(16.dp))

        // 操作按钮
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { vm.loadFollowings() }) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("加载关注")
            }
            Button(onClick = { vm.loadFollowers() }) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("加载粉丝")
            }
        }

        Spacer(Modifier.height(16.dp))

        // 标签页
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 列表
        when (selectedTab) {
            0 -> UserList(followings)
            1 -> UserList(followers)
        }
    }
}

@Composable
fun UserCard(user: BiliUser) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // 头像占位
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
fun UserList(users: List<BiliUser>) {
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无数据，点击上方按钮加载", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn {
            items(users, key = { it.mid }) { user ->
                UserListItem(user)
            }
        }
    }
}

@Composable
fun UserListItem(user: BiliUser) {
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
                Text(user.uname, style = MaterialTheme.typography.bodyLarge)
                Text("UID: ${user.mid}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
