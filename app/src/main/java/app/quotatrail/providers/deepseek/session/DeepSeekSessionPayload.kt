package app.quotatrail.providers.deepseek.session

import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekSessionPayload(val apiKey: String)
