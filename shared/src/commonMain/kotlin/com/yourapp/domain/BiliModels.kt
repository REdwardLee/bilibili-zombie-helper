package com.yourapp.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BiliUser(
    val mid: Long,
    val uname: String,
    val face: String = "",        // 头像 URL
    val sign: String = "",        // 个性签名
    @SerialName("follower") val followerCount: Int = 0,
    @SerialName("following") val followingCount: Int = 0,
    val level: Int = 0,
    val vip: VipInfo = VipInfo()
)

@Serializable
data class VipInfo(
    val type: Int = 0,
    val status: Int = 0,
    @SerialName("vip_pay_type") val payType: Int = 0
)

@Serializable
data class FollowingItem(
    val mid: Long,
    val attribute: Int = 0,     // 0=未关注, 2=已关注, 6=互关
    val mtime: Long = 0,        // 关注时间
    val uname: String = "",
    val face: String = "",
    val sign: String = ""
)

@Serializable
data class BiliVideo(
    val bvid: String = "",
    val title: String = "",
    val aid: Long = 0,
    val pic: String = "",
    val length: String = "",
    val description: String = ""
)
