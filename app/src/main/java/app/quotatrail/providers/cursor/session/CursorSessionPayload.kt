package app.quotatrail.providers.cursor.session

import kotlinx.serialization.Serializable

@Serializable
data class CursorSessionPayload(
    val cookieValue: String,
    val accountId: String? = null,
)
