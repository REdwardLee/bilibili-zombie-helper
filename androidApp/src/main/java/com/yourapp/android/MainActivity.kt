// Android 入口 — 只负责 UI 和生命周期
package com.yourapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.yourapp.android.di.AppViewModelFactory
import com.yourapp.android.ui.AppUI
import com.yourapp.android.util.CrashLogCollector

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查上次崩溃日志
        checkLastCrashLog()

        val factory = AppViewModelFactory(applicationContext)

        setContent {
            MaterialTheme {
                AppUI(factory = factory)
            }
        }
    }

    private fun checkLastCrashLog() {
        val lastCrash = CrashLogCollector.getLastCrashLog(this)
        if (lastCrash != null) {
            // 将崩溃信息存入 Intent extra，让 UI 层可以读取
            intent.putExtra("last_crash_log", CrashLogCollector.formatCrashLogForDisplay(lastCrash))
            intent.putExtra("has_crash_log", true)
            
            android.util.Log.w("MainActivity", "检测到上次崩溃:\n${CrashLogCollector.formatCrashLogForDisplay(lastCrash)}")
        }
    }
}
