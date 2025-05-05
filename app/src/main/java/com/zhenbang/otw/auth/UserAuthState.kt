package com.zhenbang.otw.auth

import net.openid.appauth.TokenResponse

data class GoogleUserInfo(
    val email: String?,
    val displayName: String?,
    val pictureUrl: String?
)

data class UserAuthState(
    val isAuthorized: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val error: String? = null,
    val needsTokenExchange: Boolean = false,
    val authCode: String? = null,
    val tokenResponse: TokenResponse? = null,
    val isLoading: Boolean = false,
    val userInfo: GoogleUserInfo? = null,
)