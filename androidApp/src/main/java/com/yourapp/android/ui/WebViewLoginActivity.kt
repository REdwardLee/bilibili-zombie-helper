package com.yourapp.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    private lateinit var viewModel: AppViewModel

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = AppViewModelFactory(applicationContext)
        viewModel = factory.create(AppViewModel::class.java)

        setContent {
            MaterialTheme {
                WebViewLoginScreen(
                    onLoginSuccess = { finish() },
                    onCancel = { finish() }
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    fun WebViewLoginScreen(
        onLoginSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        var isLoading by remember { mutableStateOf(true) }
        var progress by remember { mutableStateOf(0) }
        var pageTitle by remember { mutableStateOf("B站登录") }

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
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
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
                                    // 模拟桌面浏览器，降低风控概率
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
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
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        pageTitle = view?.title ?: "B站登录"

                                        // 检测是否已登录（URL变化到个人空间或首页）
                                        if (url != null && isLoggedInUrl(url)) {
                                            extractAndSaveCookies()
                                            MainScope().launch {
                                                delay(500) // 给CookieManager一点时间同步
                                                onLoginSuccess()
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        return false // 让WebView自己处理跳转
                                    }
                                }

                                loadUrl("https://passport.bilibili.com/login")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    private fun isLoggedInUrl(url: String): Boolean {
        // 登录成功后通常会跳转到这些页面
        return url.contains("bilibili.com") && (
            url.contains("/account/home") ||
            url.contains("space.bilibili.com") ||
            url == "https://www.bilibili.com/" ||
            url == "https://bilibili.com/" ||
            !url.contains("passport") && !url.contains("login")
        )
    }

    private fun extractAndSaveCookies() {
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie("https://bilibili.com") ?: ""
        val passportCookie = cookieManager.getCookie("https://passport.bilibili.com") ?: ""

        // 合并所有cookie
        val allCookies = buildString {
            if (cookieString.isNotEmpty()) append(cookieString)
            if (passportCookie.isNotEmpty()) {
                if (isNotEmpty()) append(";")
                append(passportCookie)
            }
        }

        if (allCookies.isNotEmpty()) {
            // 使用已有的usecase保存cookie
            MainScope().launch {
                viewModel.saveCookieAndLogin(allCookies)
            }
        }
    }
}
