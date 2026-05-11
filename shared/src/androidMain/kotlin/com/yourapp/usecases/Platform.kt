// Android 平台实现
package com.yourapp.usecases

import java.util.UUID

internal actual fun generateId(): String = UUID.randomUUID().toString()
internal actual fun currentTimestamp(): Long = System.currentTimeMillis()
