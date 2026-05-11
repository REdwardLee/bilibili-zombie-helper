package com.yourapp.android.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.data.AndroidSettingsStorage
import com.yourapp.data.BiliRepositoryImpl
import com.yourapp.domain.BiliUser
import com.yourapp.usecases.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(context: Context) : ViewModel() {
    private val storage = AndroidSettingsStorage(context.applicationContext)
    private val repo = BiliRepositoryImpl(storage)

    private val getLoginInfo = GetLoginInfoUseCase(repo)
    private val getFollowings = GetFollowingsUseCase(repo)
    private val getFollowers = GetFollowersUseCase(repo)
    private val saveCookies = SaveCookiesUseCase(repo)
    private val logoutUseCase = LogoutUseCase(repo)
    private val checkLogin = IsLoggedInUseCase(repo)

    // UI State
    private val _user = MutableStateFlow<BiliUser?>(null)
    val user: StateFlow<BiliUser?> = _user.asStateFlow()

    private val _followings = MutableStateFlow<List<BiliUser>>(emptyList())
    val followings: StateFlow<List<BiliUser>> = _followings.asStateFlow()

    private val _followers = MutableStateFlow<List<BiliUser>>(emptyList())
    val followers: StateFlow<List<BiliUser>> = _followers.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            _isLoggedIn.value = checkLogin().first()
            if (_isLoggedIn.value) {
                loadUserInfo()
            }
        }
    }

    fun saveCookieAndLogin(cookieString: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                saveCookies(cookieString)
                _isLoggedIn.value = true
                loadUserInfo()
            } catch (e: Exception) {
                _error.value = "登录失败: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            getLoginInfo().fold(
                onSuccess = { _user.value = it },
                onFailure = { _error.value = "获取用户信息失败: ${it.message}" }
            )
            _loading.value = false
        }
    }

    fun loadFollowings() {
        viewModelScope.launch {
            val uid = _user.value?.mid ?: return@launch
            _loading.value = true
            _error.value = null
            getFollowings(uid).fold(
                onSuccess = { _followings.value = it },
                onFailure = { _error.value = "获取关注列表失败: ${it.message}" }
            )
            _loading.value = false
        }
    }

    fun loadFollowers() {
        viewModelScope.launch {
            val uid = _user.value?.mid ?: return@launch
            _loading.value = true
            _error.value = null
            getFollowers(uid).fold(
                onSuccess = { _followers.value = it },
                onFailure = { _error.value = "获取粉丝列表失败: ${it.message}" }
            )
            _loading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _user.value = null
            _followings.value = emptyList()
            _followers.value = emptyList()
            _isLoggedIn.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
