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
    val refreshToken: String? = null, // Still present, maybe remove later
    val idToken: String? = null,
    val error: String? = null,
    val needsTokenExchange: Boolean = false, // Still present, maybe remove later
    val authCode: String? = null,          // Still present, maybe remove later
    val tokenResponse: TokenResponse? = null, // Still present, maybe remove later
    val isLoading: Boolean = false,
    val userInfo: GoogleUserInfo? = null,
)

