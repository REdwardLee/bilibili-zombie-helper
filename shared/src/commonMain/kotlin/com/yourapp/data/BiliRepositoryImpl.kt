package com.yourapp.data

import com.yourapp.domain.BiliUser
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BiliRepositoryImpl(
    private val storage: SettingsStorage
) : BiliRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpCookies) {
            // 从 storage 读取 cookie 手动设置到请求里
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header(HttpHeaders.Referrer, "https://space.bilibili.com")
        }
        expectSuccess = false
    }

    private suspend fun getCookieHeader(): String {
        val sessdata = storage.getString(StorageKeys.BILI_SESSDATA)
        val biliJct = storage.getString(StorageKeys.BILI_BILI_JCT)
        val dede = storage.getString(StorageKeys.BILI_DEDEUSERID)
        return buildString {
            if (sessdata.isNotEmpty()) append("SESSDATA=$sessdata; ")
            if (biliJct.isNotEmpty()) append("bili_jct=$biliJct; ")
            if (dede.isNotEmpty()) append("DedeUserID=$dede; ")
        }.trimEnd(';', ' ')
    }

    override suspend fun getLoginInfo(): Result<BiliUser> = runCatching {
        val cookie = getCookieHeader()
        require(cookie.isNotEmpty()) { "未登录，请先设置 Cookie" }

        val response = client.get("https://api.bilibili.com/x/web-interface/nav") {
            header(HttpHeaders.Cookie, cookie)
        }

        val body: BiliResponse<NavData> = response.body()
        require(body.code == 0) { "获取登录信息失败: ${body.message}" }
        
        body.data!!.toBiliUser()
    }

    override suspend fun getFollowings(
        vmid: Long,
        page: Int,
        pageSize: Int
    ): Result<List<BiliUser>> = runCatching {
        val cookie = getCookieHeader()
        
        val response = client.get("https://api.bilibili.com/x/relation/followings") {
            parameter("vmid", vmid)
            parameter("pn", page)
            parameter("ps", pageSize)
            parameter("order_type", "attention")
            header(HttpHeaders.Cookie, cookie)
        }

        val body: BiliResponse<FollowingsData> = response.body()
        require(body.code == 0) { "获取关注列表失败: ${body.message}" }
        
        body.data?.list?.map { it.toBiliUser() } ?: emptyList()
    }

    override suspend fun getFollowers(
        vmid: Long,
        page: Int,
        pageSize: Int
    ): Result<List<BiliUser>> = runCatching {
        val cookie = getCookieHeader()
        
        val response = client.get("https://api.bilibili.com/x/relation/followers") {
            parameter("vmid", vmid)
            parameter("pn", page)
            parameter("ps", pageSize)
            parameter("order", "desc")
            header(HttpHeaders.Cookie, cookie)
        }

        val body: BiliResponse<FollowingsData> = response.body()
        require(body.code == 0) { "获取粉丝列表失败: ${body.message}" }
        
        body.data?.list?.map { it.toBiliUser() } ?: emptyList()
    }

    override suspend fun getUserVideos(mid: Long, page: Int): Result<List<Any>> = runCatching {
        val cookie = getCookieHeader()
        
        val response = client.get("https://api.bilibili.com/x/space/arc/search") {
            parameter("mid", mid)
            parameter("pn", page)
            parameter("ps", 30)
            header(HttpHeaders.Cookie, cookie)
        }

        val body: BiliResponse<SpaceVideoData> = response.body()
        require(body.code == 0) { "获取视频列表失败: ${body.message}" }
        
        body.data?.list?.vlist ?: emptyList()
    }

    override suspend fun saveCookies(cookieString: String) {
        // 解析 cookie 字符串，提取关键字段
        val cookies = cookieString.split(";").map { it.trim() }
        
        cookies.forEach { cookie ->
            when {
                cookie.startsWith("SESSDATA=") -> {
                    storage.putString(StorageKeys.BILI_SESSDATA, cookie.substringAfter("SESSDATA="))
                }
                cookie.startsWith("bili_jct=") -> {
                    storage.putString(StorageKeys.BILI_BILI_JCT, cookie.substringAfter("bili_jct="))
                }
                cookie.startsWith("DedeUserID=") -> {
                    val uid = cookie.substringAfter("DedeUserID=")
                    storage.putString(StorageKeys.BILI_DEDEUSERID, uid)
                    storage.putString(StorageKeys.BILI_UID, uid)
                }
            }
        }
    }

    override fun isLoggedIn(): Flow<Boolean> = flow {
        val sessdata = storage.getString(StorageKeys.BILI_SESSDATA)
        emit(sessdata.isNotEmpty())
    }

    override suspend fun logout() {
        storage.clear()
    }

    // ---- DTOs ----

    @Serializable
    private data class BiliResponse<T>(
        val code: Int,
        val message: String = "",
        val data: T? = null
    )

    @Serializable
    private data class NavData(
        val isLogin: Boolean = false,
        val email_verified: Int = 0,
        val face: String = "",
        val level_info: LevelInfo = LevelInfo(),
        val mid: Long = 0,
        val money: Double = 0.0,
        val moral: Int = 0,
        val official: Official = Official(),
        val uname: String = "",
        val vipDueDate: Long = 0,
        val vipStatus: Int = 0,
        val vipType: Int = 0,
        val wallet: Wallet = Wallet()
    ) {
        fun toBiliUser() = BiliUser(
            mid = mid,
            uname = uname,
            face = face,
            level = level_info.current_level,
            vip = com.yourapp.domain.VipInfo(type = vipType, status = vipStatus, payType = 0)
        )
    }

    @Serializable
    private data class LevelInfo(val current_level: Int = 0)

    @Serializable
    private data class Official(val role: Int = 0, val title: String = "", val desc: String = "")

    @Serializable
    private data class Wallet(val mid: Long = 0, val bcoin_balance: Int = 0, val coupon_balance: Int = 0)

    @Serializable
    private data class FollowingsData(
        val list: List<FollowingItemDto> = emptyList(),
        val total: Int = 0
    )

    @Serializable
    private data class FollowingItemDto(
        val mid: Long = 0,
        val attribute: Int = 0,
        val mtime: Long = 0,
        val uname: String = "",
        val face: String = "",
        val sign: String = "",
        val vip: VipDto = VipDto()
    ) {
        fun toBiliUser() = BiliUser(
            mid = mid,
            uname = uname,
            face = face,
            sign = sign,
            vip = com.yourapp.domain.VipInfo(vip.vipType, vip.vipStatus)
        )
    }

    @Serializable
    private data class VipDto(val vipType: Int = 0, val vipStatus: Int = 0)

    @Serializable
    private data class SpaceVideoData(val list: VideoList? = null)

    @Serializable
    private data class VideoList(val vlist: List<Any> = emptyList())
}
