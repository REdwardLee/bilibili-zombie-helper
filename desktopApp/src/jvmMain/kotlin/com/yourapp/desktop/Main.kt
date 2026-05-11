package com.yourapp.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yourapp.desktop.di.AppController
import com.yourapp.domain.BiliUser

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "B站助手",
        state = rememberWindowState(width = 480.dp, height = 800.dp)
    ) {
        MaterialTheme {
            DesktopApp()
        }
    }
}

@Composable
fun DesktopApp() {
    val controller = remember { AppController() }
    val isLoggedIn by controller.isLoggedIn.collectAsState()
    val error by controller.error.collectAsState()
    val loading by controller.loading.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it, actionLabel = "确定")
            controller.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isLoggedIn) {
                TopAppBar(
                    title = { Text("B站助手") },
                    actions = {
                        IconButton(onClick = { controller.logout() }) {
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
                LoginScreen(controller)
            } else {
                MainScreen(controller)
            }
        }
    }
}

@Composable
fun LoginScreen(controller: AppController) {
    var cookieInput by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("B站助手", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("粘贴浏览器 Cookie 即可登录", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = cookieInput,
            onValueChange = { cookieInput = it },
            label = { Text("Cookie 字符串") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 6
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { controller.saveCookieAndLogin(cookieInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = cookieInput.isNotBlank()
        ) {
            Text("登录")
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "获取方式：浏览器打开 bilibili.com → F12 → Application/Storage → Cookies → 复制 SESSDATA 等完整 cookie 字符串",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MainScreen(controller: AppController) {
    val user by controller.user.collectAsState()
    val followings by controller.followings.collectAsState()
    val followers by controller.followers.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("关注列表", "粉丝列表")

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        user?.let { UserCard(it) }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { controller.loadFollowings() }) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("加载关注")
            }
            Button(onClick = { controller.loadFollowers() }) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("加载粉丝")
            }
        }

        Spacer(Modifier.height(16.dp))

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
