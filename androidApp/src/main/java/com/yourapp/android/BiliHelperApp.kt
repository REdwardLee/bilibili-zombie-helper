package com.yourapp.android

import android.app.Application
import com.yourapp.android.util.CrashLogCollector
import kotlin.system.exitProcess

/**
 * 全局 Application 类
 * 设置全局异常捕获器，收集所有未处理的崩溃
 */
class BiliHelperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 保存崩溃日志
            CrashLogCollector.saveCrashLog(
                this,
                throwable,
                mapOf(
                    "source" to "global_uncaught_exception",
                    "thread" to thread.name,
                    "isMainThread" to (thread.name == "main").toString()
                )
            )
            
            android.util.Log.e("BiliHelperApp", "Uncaught exception in thread ${thread.name}", throwable)
            
            // 调用默认处理器（让系统正常崩溃，显示崩溃对话框）
            defaultHandler?.uncaughtException(thread, throwable)
            
            // 如果默认处理器没有退出，强制退出
            exitProcess(1)
        }
    }
}
