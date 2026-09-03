package app.quotatrail.storage.secure

interface SecureSessionStore {
    suspend fun save(envelope: ProviderSessionEnvelope)

    suspend fun load(
        providerId: String,
        localAccountId: String,
    ): ProviderSessionEnvelope?

    suspend fun delete(
        providerId: String,
        localAccountId: String,
    )
}
