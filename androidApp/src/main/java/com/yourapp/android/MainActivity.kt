// Android 入口 — 只负责 UI 和生命周期
package com.yourapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.yourapp.android.di.AppViewModelFactory
import com.yourapp.android.ui.AppUI

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = AppViewModelFactory(applicationContext)

        setContent {
            MaterialTheme {
                AppUI(factory = factory)
            }
        }
    }
}
