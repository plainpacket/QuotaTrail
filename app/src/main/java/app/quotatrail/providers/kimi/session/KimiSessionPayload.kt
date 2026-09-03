package app.quotatrail.providers.kimi.session

import kotlinx.serialization.Serializable

@Serializable
data class KimiSessionPayload(
    val cookieValue: String,
    val accountId: String? = null,
    val jwtExpiryEpochSeconds: Long? = null,
)
