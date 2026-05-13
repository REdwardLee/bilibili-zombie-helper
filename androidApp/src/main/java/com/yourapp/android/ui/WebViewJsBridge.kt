package com.yourapp.android.ui

import android.webkit.JavascriptInterface

/** WebView JS Bridge: 页面注入 JS 检测关注状态后回调 */
class WebViewJsBridge(
    private val onResult: (mid: Long, isFollowing: Boolean) -> Unit
) {
    @JavascriptInterface
    fun reportStatus(mid: Long, isFollowing: Boolean) {
        onResult(mid, isFollowing)
    }
}
