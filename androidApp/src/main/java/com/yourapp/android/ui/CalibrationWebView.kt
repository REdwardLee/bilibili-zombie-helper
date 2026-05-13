package com.yourapp.android.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * WebView 校准组件：加载 UP 主主页，检测关注状态。
 *
 * 修复要点：
 * 1. key = mid 确保旧 WebView 销毁
 * 2. Cookie 逐个设置
 * 3. JS 防重复回调（window.__reported）
 * 4. 5 秒绝对超时
 */
@Composable
fun CalibrationWebView(
    mid: Long,
    cookieString: String,
    onResult: (mid: Long, isFollowing: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // key = mid 确保 mid 变化时销毁旧 WebView
    key(mid) {
        val bridge = WebViewJsBridge { resultMid, isFollowing ->
            onResult(resultMid, isFollowing)
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // 逐个设置 cookie（不能传 "a=1; b=2" 这种组合字符串）
                    CookieManager.getInstance().apply {
                        val cookies = parseCookieString(cookieString)
                        cookies.forEach { (name, value) ->
                            setCookie("https://m.bilibili.com", "$name=$value")
                        }
                        flush()
                    }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 10; SM-G960U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 等 800ms 让 SPA 渲染完再检测
                            val js = """
                                setTimeout(function() {
                                    (function() {
                                        if (window.__calibrationReported) return;
                                        window.__calibrationReported = true;

                                        function detect() {
                                            // 1. 先查常见 class
                                            var selectors = [
                                                '.follow-btn', '.follow-btn-wrap',
                                                '[class*="follow-btn"]', '[class*="FollowBtn"]',
                                                '[data-module="follow"]'
                                            ];
                                            for (var s = 0; s < selectors.length; s++) {
                                                var el = document.querySelector(selectors[s]);
                                                if (el) {
                                                    var text = (el.textContent || el.innerText || '').trim();
                                                    if (text.indexOf('已关注') !== -1 || text.indexOf('相互关注') !== -1 || text.indexOf('回关') !== -1) return {found: true, following: true};
                                                    if (text.indexOf('关注') !== -1 || text.indexOf('+') !== -1) return {found: true, following: false};
                                                }
                                            }

                                            // 2. fallback：遍历按钮和链接
                                            var all = document.querySelectorAll('button, a, span, div');
                                            for (var i = 0; i < all.length; i++) {
                                                var t = (all[i].textContent || all[i].innerText || '').trim();
                                                if (t === '已关注' || t === '已关注Ta' || t === '相互关注' || t === '回关')
                                                    return {found: true, following: true};
                                                if (t === '关注' || t === '+ 关注' || t === '+关注')
                                                    return {found: true, following: false};
                                            }
                                            return {found: false, following: false};
                                        }

                                        var result = detect();
                                        if (result.found) {
                                            window.AndroidBridge.reportStatus($mid, result.following);
                                        } else {
                                            // 按钮没找到，可能是页面结构不同，fallback 未关注
                                            window.AndroidBridge.reportStatus($mid, false);
                                        }
                                    })();
                                }, 800);

                                // 绝对超时：6 秒后不管有没有结果都回调
                                setTimeout(function() {
                                    if (!window.__calibrationReported) {
                                        window.__calibrationReported = true;
                                        window.AndroidBridge.reportStatus($mid, false);
                                    }
                                }, 6000);
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }
                    loadUrl("https://m.bilibili.com/space/$mid")
                }
            },
            modifier = modifier.size(1.dp).alpha(0f),
            update = {}
        )
    }
}

/** 解析 "a=1; b=2" 格式的 cookie 字符串为 Map */
private fun parseCookieString(cookie: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    cookie.split(";").forEach { part ->
        val trimmed = part.trim()
        if (trimmed.isNotEmpty()) {
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isNotEmpty()) result[name] = value
            }
        }
    }
    return result
}
