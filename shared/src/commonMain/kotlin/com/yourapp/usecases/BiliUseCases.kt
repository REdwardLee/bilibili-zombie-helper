package com.yourapp.usecases

import com.yourapp.data.BiliRepository
import com.yourapp.domain.BiliUser
import kotlinx.coroutines.flow.Flow

class GetLoginInfoUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(): Result<BiliUser> = repo.getLoginInfo()
}

class GetFollowingsUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>> =
        repo.getFollowings(vmid, page, pageSize)
}

class GetFollowersUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(vmid: Long, page: Int = 1, pageSize: Int = 50): Result<List<BiliUser>> =
        repo.getFollowers(vmid, page, pageSize)
}

class GetUserLastUpdateTimeUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(mid: Long): Result<Long> = repo.getUserLastUpdateTime(mid)
}

class SaveCookiesUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(cookieString: String) = repo.saveCookies(cookieString)
}

class LogoutUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke() = repo.logout()
}

class ModifyRelationUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(fid: Long, act: Int): Result<Unit> = repo.modifyRelation(fid, act)
}

class GetRelationStatusUseCase(private val repo: BiliRepository) {
    suspend operator fun invoke(mid: Long): Result<Int> = repo.getRelationStatus(mid)
}

class IsLoggedInUseCase(private val repo: BiliRepository) {
    operator fun invoke(): Flow<Boolean> = repo.isLoggedIn()
}
