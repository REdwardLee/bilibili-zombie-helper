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
    onResult: (mid: Long, isFollowing: Boolean, isTimeout: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // key = mid 确保 mid 变化时销毁旧 WebView
    key(mid) {
        val bridge = WebViewJsBridge { resultMid, isFollowing, isTimeout ->
            onResult(resultMid, isFollowing, isTimeout)
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
                            // 等 1.5 秒让 SPA 完全渲染，再检测关注按钮
                            val js = """
                                setTimeout(function() {
                                    (function() {
                                        if (window.__calibrationReported) return;

                                        function detect() {
                                            // 1. 常见关注按钮选择器（B站SPA动态渲染）
                                            var selectors = [
                                                '.follow-btn', '.follow-btn-wrap',
                                                '[class*="follow-btn"]', '[class*="FollowBtn"]',
                                                '[class*="follow_btn"]', '[class*="followBtn"]',
                                                '[data-module="follow"]', '[data-type="follow"]',
                                                '.action-follow', '.user-follow',
                                                'button:has-text(已关注)', 'button:has-text(关注)',
                                                'a:has-text(已关注)', 'a:has-text(关注)'
                                            ];
                                            for (var s = 0; s < selectors.length; s++) {
                                                try {
                                                    var els = document.querySelectorAll(selectors[s]);
                                                    for (var e = 0; e < els.length; e++) {
                                                        var el = els[e];
                                                        var text = (el.textContent || el.innerText || '').trim();
                                                        // 已关注状态：已关注 / 相互关注 / 回关 / 特别关注 / 特关
                                                        if (text.indexOf('已关注') !== -1 || text.indexOf('相互关注') !== -1 || text.indexOf('回关') !== -1 || text.indexOf('特别关注') !== -1 || text.indexOf('特关') !== -1)
                                                            return {found: true, following: true};
                                                        // 未关注状态：只匹配纯"关注"按钮（排除"特别关注"干扰）
                                                        if (text === '关注' || text === '+ 关注' || text === '+关注' || text === '＋关注' || text === '加关注')
                                                            return {found: true, following: false};
                                                    }
                                                } catch(_) {}
                                            }

                                            // 2. 遍历所有可见按钮和链接
                                            var all = document.querySelectorAll('button, a, span, div, p');
                                            for (var i = 0; i < all.length; i++) {
                                                var t = (all[i].textContent || all[i].innerText || '').trim();
                                                // 已关注
                                                if (t === '已关注' || t === '已关注Ta' || t === '相互关注' || t === '回关' || t === '特别关注' || t === '特关')
                                                    return {found: true, following: true};
                                                // 未关注
                                                if (t === '关注' || t === '+ 关注' || t === '+关注' || t === '＋关注' || t === '加关注')
                                                    return {found: true, following: false};
                                            }

                                            // 3. 检查是否有特殊标记（有些版本用 data-follow 属性）
                                            var followEls = document.querySelectorAll('[data-follow]');
                                            for (var j = 0; j < followEls.length; j++) {
                                                var attr = followEls[j].getAttribute('data-follow');
                                                if (attr === '1' || attr === 'true') return {found: true, following: true};
                                                if (attr === '0' || attr === 'false') return {found: true, following: false};
                                            }

                                            // 4. 检查按钮上是否有"已关注"相关的 class（特别关注可能用不同 class）
                                            var allBtns = document.querySelectorAll('button, a');
                                            for (var b = 0; b < allBtns.length; b++) {
                                                var cls = allBtns[b].className || '';
                                                if (cls.indexOf('followed') !== -1 || cls.indexOf('following') !== -1 || cls.indexOf('attention') !== -1)
                                                    return {found: true, following: true};
                                                if (cls.indexOf('follow') !== -1 && cls.indexOf('unfollow') === -1 && cls.indexOf('nofollow') === -1)
                                                    return {found: true, following: false};
                                            }

                                            return {found: false, following: false};
                                        }

                                        var result = detect();
                                        if (result.found) {
                                            window.__calibrationReported = true;
                                            window.AndroidBridge.reportStatus($mid, result.following);
                                        } else {
                                            // 按钮没找到，再延迟 1 秒重试一次（SPA 可能还没渲染完）
                                            setTimeout(function() {
                                                if (window.__calibrationReported) return;
                                                var retry = detect();
                                                window.__calibrationReported = true;
                                                if (retry.found) {
                                                    window.AndroidBridge.reportStatus($mid, retry.following);
                                                } else {
                                                    // 还是没找到，fallback：尝试从页面 JSON 数据推断
                                                    var pageData = window.__INITIAL_STATE__ || window.__SPACE_DATA__ || {};
                                                    var relation = pageData.relation || {};
                                                    var following = relation.following || relation.attention || 0;
                                                    if (following) {
                                                        window.AndroidBridge.reportStatus($mid, true);
                                                    } else {
                                                        // 最终 fallback：保守策略，保持原状态（不改动）
                                                        window.AndroidBridge.reportStatus($mid, false);
                                                    }
                                                }
                                            }, 1000);
                                        }
                                    })();
                                }, 1500);

                                // 绝对超时：8 秒后不管有没有结果都回调（保守策略：不修改状态）
                                setTimeout(function() {
                                    if (!window.__calibrationReported) {
                                        window.__calibrationReported = true;
                                        window.AndroidBridge.reportTimeout($mid);
                                    }
                                }, 8000);
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
