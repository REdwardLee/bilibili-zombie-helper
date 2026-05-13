package com.yourapp.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yourapp.android.di.AppViewModel
import com.yourapp.android.di.AppViewModelFactory
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WebViewLoginActivity : ComponentActivity() {

    private val TAG = "WebViewLogin"
    private lateinit var viewModel: AppViewModel

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = AppViewModelFactory(applicationContext)
        viewModel = factory.create(AppViewModel::class.java)

        // 先同步清除所有 cookie，确保没有上次登录的残留
        clearAllCookiesAndCache {
            setContent {
                MaterialTheme {
                    WebViewLoginScreen(
                        onCancel = {
                            viewModel.clearError()
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * 彻底清除 WebView 的所有 cookie 和缓存，完成后执行 callback
     */
    private fun clearAllCookiesAndCache(onComplete: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { _ ->
            cookieManager.flush()
            onComplete()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun WebViewLoginScreen(
        onCancel: () -> Unit
    ) {
        var isLoading by remember { mutableStateOf(true) }
        var isVerifying by remember { mutableStateOf(false) }
        var verifyError by remember { mutableStateOf<String?>(null) }
        var progress by remember { mutableStateOf(0) }
        var pageTitle by remember { mutableStateOf("B站登录") }

        val loginState by viewModel.isLoggedIn.collectAsStateWithLifecycle()
        val errorState by viewModel.error.collectAsStateWithLifecycle()

        // 验证成功 → 关闭页面
        LaunchedEffect(loginState) {
            if (loginState) {
                delay(300)
                setResult(RESULT_OK)
                finish()
            }
        }

        // 验证失败 → 显示错误并关闭 WebView
        LaunchedEffect(errorState) {
            if (errorState != null) {
                isVerifying = false
                verifyError = errorState
                viewModel.clearError()
                delay(2500)
                finish()
            }
        }

        // 超时保护：验证超过 10 秒无响应
        LaunchedEffect(isVerifying) {
            if (isVerifying) {
                delay(10000)
                if (isVerifying) {
                    isVerifying = false
                    verifyError = "验证超时，请尝试手动输入 Cookie 登录"
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(pageTitle) },
                    actions = {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLoading || isVerifying) {
                    LinearProgressIndicator(
                        progress = if (isVerifying) 0.5f else progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when {
                    isVerifying -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("正在验证登录状态...")
                            }
                        }
                    }
                    verifyError != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("❌ 验证失败", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(verifyError!!, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(16.dp))
                                Text("请尝试手动输入 Cookie 登录", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { finish() }) {
                                    Text("返回")
                                }
                            }
                        }
                    }
                    else -> {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        // 使用移动端 User-Agent
                                        userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            isLoading = true
                                            Log.d(TAG, "onPageStarted: $url")
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoading = false
                                            pageTitle = view?.title ?: "B站登录"
                                            Log.d(TAG, "onPageFinished: $url, isVerifying=$isVerifying")

                                            // 避免重复触发
                                            if (isVerifying) return

                                            // 非登录页面 + cookie 中有 SESSDATA = 登录成功
                                            if (url != null && isNonLoginBiliPage(url)) {
                                                val cookieManager = CookieManager.getInstance()
                                                val rawCookie = cookieManager.getCookie("https://www.bilibili.com") ?: ""
                                                Log.d(TAG, "检测 cookie: ${rawCookie.take(200)}")

                                                if (rawCookie.contains("SESSDATA")) {
                                                    Log.d(TAG, "检测到 SESSDATA，开始提取")
                                                    isVerifying = true
                                                    extractAndSaveCookies()
                                                } else {
                                                    Log.d(TAG, "暂无 SESSDATA，等待用户完成登录")
                                                }
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            return false
                                        }
                                    }

                                    loadUrl("https://passport.bilibili.com/login")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断是否是 bilibili.com 的非登录页面。
     * 只要是非 passport/login 的 bilibili.com 页面，都尝试检测 cookie。
     */
    private fun isNonLoginBiliPage(url: String): Boolean {
        if (!url.contains("bilibili.com")) return false
        if (url.contains("passport.bilibili.com")) return false
        if (url.contains("/login")) return false
        return true
    }

    private fun extractAndSaveCookies() {
        MainScope().launch {
            // 给 CookieManager 一点时间完成同步
            delay(800)

            val cookieManager = CookieManager.getInstance()
            val rawCookie = cookieManager.getCookie("https://www.bilibili.com") ?: ""

            Log.d(TAG, "原始 cookie: ${rawCookie.take(300)}")

            // 去重：同一 name 只保留最后一次出现
            val uniqueCookies = rawCookie
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associateBy { it.substringBefore("=") }
                .values
                .joinToString("; ")

            Log.d(TAG, "去重后: ${uniqueCookies.take(300)}, 含SESSDATA=${uniqueCookies.contains("SESSDATA")}")

            if (uniqueCookies.isNotEmpty() && uniqueCookies.contains("SESSDATA")) {
                viewModel.saveCookieAndLogin(uniqueCookies)
            } else {
                // 降级：从 passport 域名再试
                val passportCookie = cookieManager.getCookie("https://passport.bilibili.com") ?: ""
                if (passportCookie.contains("SESSDATA")) {
                    val cleaned = passportCookie
                        .split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .associateBy { it.substringBefore("=") }
                        .values
                        .joinToString("; ")
                    viewModel.saveCookieAndLogin(cleaned)
                } else {
                    viewModel.saveCookieAndLogin("")
                }
            }
        }
    }
}
