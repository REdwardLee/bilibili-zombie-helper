package com.yourapp.data

import com.yourapp.domain.BiliUser
import com.yourapp.domain.BiliVideo
import kotlinx.coroutines.flow.Flow

interface BiliRepository {
    /** 从 Cookie 获取当前登录用户信息 */
    suspend fun getLoginInfo(): Result<BiliUser>

    /** 获取关注列表 */
    suspend fun getFollowings(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>>

    /** 获取粉丝列表 */
    suspend fun getFollowers(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>>

    /** 获取关注列表总数 */
    suspend fun getFollowingsTotal(vmid: Long): Result<Int>

    /** 获取粉丝列表总数 */
    suspend fun getFollowersTotal(vmid: Long): Result<Int>

    /** 获取用户最后更新时间(视频/动态,毫秒时间戳,0表示无法获取,-1被封禁,-2无动态) */
    suspend fun getUserLastUpdateTime(mid: Long): Result<Long>

    /** 查询与指定用户的关注关系,返回 attribute (0=未关注, 2=已关注, 6=互关) */
    suspend fun getRelationStatus(mid: Long): Result<Int>

    /** 修改关注关系 (act: 1=关注, 2=取关) */
    suspend fun modifyRelation(fid: Long, act: Int): Result<Unit>

    /** 设为/取消特别关注 (isSpecial: true=设为特别关注, false=取消特别关注回到普通关注) */
    suspend fun setSpecialFollow(fid: Long, isSpecial: Boolean): Result<Unit>

    /** 从 Cookie 字符串解析并保存 */
    suspend fun saveCookies(cookieString: String)

    /** 当前是否有登录态 */
    fun isLoggedIn(): Flow<Boolean>

    /** 退出登录 */
    suspend fun logout()
}
