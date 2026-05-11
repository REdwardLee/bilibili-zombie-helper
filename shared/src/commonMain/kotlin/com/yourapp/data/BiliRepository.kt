package com.yourapp.data

import com.yourapp.domain.BiliUser
import kotlinx.coroutines.flow.Flow

interface BiliRepository {
    /** 从 Cookie 获取当前登录用户信息 */
    suspend fun getLoginInfo(): Result<BiliUser>
    
    /** 获取关注列表 */
    suspend fun getFollowings(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>>
    
    /** 获取粉丝列表 */
    suspend fun getFollowers(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>>
    
    /** 获取用户的动态 / 视频列表（简化版） */
    suspend fun getUserVideos(mid: Long, page: Int = 1): Result<List<Any>>
    
    /** 从 Cookie 字符串解析并保存 */
    suspend fun saveCookies(cookieString: String)
    
    /** 当前是否有登录态 */
    fun isLoggedIn(): Flow<Boolean>
    
    /** 退出登录 */
    suspend fun logout()
}
