package com.yourapp.data

import com.yourapp.domain.BiliUser
import com.yourapp.domain.BiliVideo
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
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        // 不启用 HttpCookies 插件，完全手动管理 cookie
        defaultRequest {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header(HttpHeaders.Referrer, "https://space.bilibili.com")
        }
        expectSuccess = false
    }

    private suspend fun getCookieHeader(): String {
        // 优先使用完整的原始 cookie 字符串
        val fullCookie = storage.getString(StorageKeys.BILI_FULL_COOKIE)
        if (fullCookie.isNotEmpty()) {
            return fullCookie
        }
        // 降级：用拼接的三个字段
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

    override suspend fun getFollowingsTotal(vmid: Long): Result<Int> = runCatching {
        val cookie = getCookieHeader()
        val response = client.get("https://api.bilibili.com/x/relation/stat") {
            parameter("vmid", vmid)
            header(HttpHeaders.Cookie, cookie)
        }
        val body: BiliResponse<RelationStatData> = response.body()
        require(body.code == 0) { "获取关注总数失败: ${body.message}" }
        body.data?.following ?: 0
    }

    override suspend fun getFollowersTotal(vmid: Long): Result<Int> = runCatching {
        val cookie = getCookieHeader()
        val response = client.get("https://api.bilibili.com/x/relation/stat") {
            parameter("vmid", vmid)
            header(HttpHeaders.Cookie, cookie)
        }
        val body: BiliResponse<RelationStatData> = response.body()
        require(body.code == 0) { "获取粉丝总数失败: ${body.message}" }
        body.data?.follower ?: 0
    }

    override suspend fun getUserLastUpdateTime(mid: Long): Result<Long> = runCatching {
        val cookie = getCookieHeader()

        // 获取最新 2 条动态，取最近时间
        val dynamicResponse = client.get("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space") {
            parameter("host_mid", mid)
            parameter("offset", "")
            parameter("page", 1)
            parameter("ps", 2)
            parameter("timezone_offset", -480)
            parameter("platform", "web")
            header(HttpHeaders.Cookie, cookie)
        }

        val dynamicBody: BiliResponse<DynamicData> = dynamicResponse.body()

        // 区分被封禁和其他API错误
        if (dynamicBody.code != 0) {
            val msg = dynamicBody.message.lowercase()
            // 被封禁/账号异常的特征：-404 或关键词
            if (dynamicBody.code == -404 || dynamicBody.code == -403 ||
                msg.contains("封禁") || msg.contains("冻结") || msg.contains("小黑屋") ||
                msg.contains("账号") || msg.contains("不存在") || msg.contains("木有")
            ) {
                return@runCatching -1L  // -1 = 被封禁
            }
            throw Exception("获取动态失败: ${dynamicBody.message}")
        }

        val items = dynamicBody.data?.items ?: emptyList()
        if (items.isEmpty()) {
            // 动态为空时，查空间信息确认是否被封禁（被封禁账号动态API返回空列表）
            val spaceResponse = client.get("https://api.bilibili.com/x/space/acc/info") {
                parameter("mid", mid)
                header(HttpHeaders.Cookie, cookie)
            }
            val spaceBody: BiliResponse<SpaceInfoData> = spaceResponse.body()
            if (spaceBody.code == 0 && spaceBody.data?.silence == 1) {
                return@runCatching -1L  // 被封禁
            }
            return@runCatching -2L  // 无动态（从未发过或已清空）
        }

        // 取最新 2 条中的最大 pub_ts（秒级时间戳）
        val timestamps = items.mapNotNull { item ->
            val pubTs = item.modules?.moduleAuthor?.pubTs
            // pub_ts 是秒级时间戳，转毫秒
            if (pubTs != null && pubTs > 0) pubTs * 1000L else null
        }

        timestamps.maxOrNull() ?: 0L
    }

    override suspend fun getRelationStatus(mid: Long): Result<Int> = runCatching {
        val cookie = getCookieHeader()
        val response = client.get("https://api.bilibili.com/x/relation?fid=$mid") {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.Referrer, "https://space.bilibili.com")
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 10; SM-G960U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            header("X-Requested-With", "XMLHttpRequest")
        }

        val bodyText = response.bodyAsText()
        // 被风控时返回 HTML（以 < 开头），不是 JSON
        if (bodyText.trimStart().startsWith("<")) {
            // 把完整 HTML 放在异常里，带特殊标记方便上层解析保存
            throw Exception("HTML_BLOCK\n$bodyText")
        }

        // 安全解析 JSON
        val relationResp = try {
            json.decodeFromString(RelationResponse.serializer(), bodyText)
        } catch (e: Exception) {
            val preview = bodyText.take(200).replace("\n", " ")
            throw Exception("JSON解析失败: ${e.message?.take(60)}... 原始内容: $preview")
        }

        require(relationResp.code == 0) { "查询关系失败 (code=${relationResp.code})" }
        relationResp.data?.attribute ?: 0
    }

    override suspend fun modifyRelation(fid: Long, act: Int): Result<Unit> = runCatching {
        val cookie = getCookieHeader()
        val biliJct = storage.getString(StorageKeys.BILI_BILI_JCT)
        require(biliJct.isNotEmpty()) { "缺少 CSRF token" }

        val myMid = storage.getString(StorageKeys.BILI_UID)
        val referer = if (myMid.isNotEmpty()) "https://space.bilibili.com/$myMid" else "https://space.bilibili.com"

        val response = client.post("https://api.bilibili.com/x/relation/modify") {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.Referrer, referer)
            header(HttpHeaders.Origin, "https://space.bilibili.com")
            setBody("fid=$fid&act=$act&re_src=11&csrf=$biliJct")
        }

        val bodyText = response.bodyAsText()
        // 手动解析JSON
        val code = bodyText.substringAfter("\"code\":").substringBefore(",").trim().toIntOrNull() ?: -999
        val message = bodyText.substringAfter("\"message\":\"").substringBefore("\",") ?: "未知错误"

        require(code == 0) { "操作失败: $message (code=$code)" }
    }

    override suspend fun saveCookies(cookieString: String) {
        // 保存完整的原始 cookie 字符串（用于 API 请求时原样发送）
        storage.putString(StorageKeys.BILI_FULL_COOKIE, cookieString)

        // 同时解析关键字段用于 UID 等用途
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
        val fullCookie = storage.getString(StorageKeys.BILI_FULL_COOKIE)
        emit(fullCookie.isNotEmpty())
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
            vip = com.yourapp.domain.VipInfo(vip.vipType, vip.vipStatus),
            attribute = attribute
        )
    }

    @Serializable
    private data class VipDto(val vipType: Int = 0, val vipStatus: Int = 0)

    @Serializable
    private data class SpaceVideoData(val list: VideoList? = null)

    @Serializable
    private data class VideoList(val vlist: List<BiliVideoItem> = emptyList())

    @Serializable
    private data class BiliVideoItem(
        val bvid: String = "",
        val title: String = "",
        val created: Long = 0,
        val pubdate: Long = 0
    )

    @Serializable
    private data class DynamicData(
        val items: List<DynamicItem> = emptyList()
    )

    @Serializable
    private data class DynamicItem(
        val modules: DynamicModules? = null
    )

    @Serializable
    private data class DynamicModules(
        val moduleTag: DynamicTag? = null,
        val moduleAuthor: DynamicAuthor? = null
    )

    @Serializable
    private data class DynamicTag(
        val text: String = ""
    )

    @Serializable
    private data class DynamicAuthor(
        @kotlinx.serialization.SerialName("pub_ts")
        val pubTs: Long = 0
    )

    @Serializable
    private data class RelationStatData(
        val following: Int = 0,
        val whisper: Int = 0,
        val black: Int = 0,
        val follower: Int = 0
    )

    @Serializable
    private data class SpaceInfoData(
        val mid: Long = 0,
        val name: String = "",
        val silence: Int = 0  // 0=正常, 1=被封禁
    )

    @Serializable
    private data class RelationResponse(
        val code: Int,
        val data: RelationData? = null
    )

    @Serializable
    private data class RelationData(
        val mid: Long = 0,
        val attribute: Int = 0,
        val mtime: Long = 0
    )
}
