// 业务实体 — 纯数据，无平台依赖
package com.yourapp.domain

/**
 * 用户实体
 * 所有平台共享的数据结构
 */
data class User(
    val id: String,
    val name: String,
    val email: String
)

/**
 * 任务实体
 */
data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = 0L
)
