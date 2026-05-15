package com.yourapp.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourapp.android.di.AppViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 调试功能枚举 */
enum class DebugFeature(val label: String, val icon: String) {
    BATCH_CALIBRATE("全校准", "🔧"),
    CALIBRATE_VISIBLE("校准当前可见", "👁"),
    CLEAR_ZOMBIE("清空僵尸UP", "🗑"),
    CLEAR_LOGS("清除日志", "📝"),
    SAVE_LOGS("保存日志", "💾"),
    OPEN_LOG_DIR("打开日志目录", "📁");

    companion object {
        val ALL = listOf(BATCH_CALIBRATE, CALIBRATE_VISIBLE, CLEAR_ZOMBIE, CLEAR_LOGS, SAVE_LOGS, OPEN_LOG_DIR)
    }
}

/** 功能选择设置对话框 */
@Composable
fun DebugFeatureSettingsDialog(
    onDismiss: () -> Unit,
    vm: AppViewModel
) {
    val enabledFeatures by vm.debugFeatures.collectAsStateWithLifecycle()
    val availableFeatures = DebugFeature.ALL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试功能设置") },
        text = {
            Column {
                Text(
                    "已启用功能",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 已启用功能列表
                if (enabledFeatures.isEmpty()) {
                    Text(
                        "暂无启用功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    enabledFeatures.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${feature.icon} ${feature.label}")
                            IconButton(
                                onClick = { vm.removeDebugFeature(feature) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "可用功能",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 未启用的功能
                val disabledFeatures = availableFeatures.filter { it !in enabledFeatures }
                if (disabledFeatures.isEmpty()) {
                    Text(
                        "所有功能已启用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    disabledFeatures.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${feature.icon} ${feature.label}")
                            IconButton(
                                onClick = { vm.addDebugFeature(feature) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
