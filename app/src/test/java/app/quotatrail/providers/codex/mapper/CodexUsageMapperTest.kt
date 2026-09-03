package app.quotatrail.providers.codex.mapper

import app.quotatrail.domain.model.LocalAccountId
import app.quotatrail.domain.model.ProviderAccountId
import app.quotatrail.domain.model.ProviderId
import app.quotatrail.domain.quota.QuotaSnapshotSource
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.providers.codex.dto.CodexCreditsDto
import app.quotatrail.providers.codex.dto.CodexRateLimitDto
import app.quotatrail.providers.codex.dto.CodexUsageResponseDto
import app.quotatrail.providers.codex.dto.CodexWindowDto
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CodexUsageMapperTest {
    private val mapper = CodexUsageMapper()

    @Test
    fun `maps primary to five hour and secondary to weekly`() {
        val fetchedAt = Instant.parse("2026-05-23T10:00:00Z")
        val snapshot = mapper.map(
            dto = CodexUsageResponseDto(
                planType = "plus",
                rateLimit = CodexRateLimitDto(
                    primaryWindow = CodexWindowDto(
                        usedPercent = 62,
                        resetAt = 1770000000L,
                        limitWindowSeconds = 18_000,
                    ),
                    secondaryWindow = CodexWindowDto(
                        usedPercent = 41,
                        resetAt = 1770500000L,
                        limitWindowSeconds = 604_800,
                    ),
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = ProviderAccountId("provider-account"),
            fetchedAt = fetchedAt,
            source = QuotaSnapshotSource.ManualRefresh,
        )

        assertEquals(ProviderId("codex"), snapshot.providerId)
        assertEquals("codex:local-account:1779530400000:ManualRefresh", snapshot.snapshotId.value)
        assertEquals(LocalAccountId("local-account"), snapshot.localAccountId)
        assertEquals(ProviderAccountId("provider-account"), snapshot.providerAccountId)
        assertEquals(fetchedAt, snapshot.fetchedAt)
        assertEquals(QuotaSnapshotSource.ManualRefresh, snapshot.source)
        assertEquals("plus", snapshot.planType)
        assertNull(snapshot.credits)
        assertNull(snapshot.responseDigest)

        val fiveHour = snapshot.windows[0]
        assertEquals("five_hour", fiveHour.windowId.value)
        assertEquals("quota_window_five_hour", fiveHour.titleKey)
        assertEquals(62, fiveHour.usedPercent)
        assertEquals(Instant.ofEpochSecond(1770000000L), fiveHour.resetAt)
        assertEquals(18_000, fiveHour.limitWindowSeconds)
        assertEquals(true, fiveHour.isPrimaryCandidate)
        assertEquals(QuotaWindowAvailability.Available, fiveHour.availability)

        val weekly = snapshot.windows[1]
        assertEquals("weekly", weekly.windowId.value)
        assertEquals("quota_window_weekly", weekly.titleKey)
        assertEquals(41, weekly.usedPercent)
        assertEquals(Instant.ofEpochSecond(1770500000L), weekly.resetAt)
        assertEquals(604_800, weekly.limitWindowSeconds)
        assertEquals(true, weekly.isPrimaryCandidate)
        assertEquals(QuotaWindowAvailability.Available, weekly.availability)
    }

    @Test
    fun `maps a lone seven day primary window as weekly instead of five hour`() {
        val snapshot = mapper.map(
            dto = CodexUsageResponseDto(
                rateLimit = CodexRateLimitDto(
                    primaryWindow = CodexWindowDto(
                        usedPercent = 37,
                        resetAt = 1770500000L,
                        limitWindowSeconds = 604_800,
                    ),
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
        )

        val fiveHour = snapshot.windows.first { it.windowId.value == "five_hour" }
        assertEquals(QuotaWindowAvailability.Missing, fiveHour.availability)
        assertNull(fiveHour.usedPercent)

        val weekly = snapshot.windows.first { it.windowId.value == "weekly" }
        assertEquals(QuotaWindowAvailability.Available, weekly.availability)
        assertEquals(37, weekly.usedPercent)
        assertEquals(604_800, weekly.limitWindowSeconds)
    }

    @Test
    fun `out of range reset value marks only affected window decode failed`() {
        val snapshot = try {
            mapper.map(
                dto = CodexUsageResponseDto(
                    rateLimit = CodexRateLimitDto(
                        primaryWindow = CodexWindowDto(
                            usedPercent = 62,
                            resetAt = Long.MAX_VALUE,
                            limitWindowSeconds = 18_000,
                        ),
                        secondaryWindow = CodexWindowDto(
                            usedPercent = 41,
                            resetAt = 1770500000L,
                            limitWindowSeconds = 604_800,
                        ),
                    ),
                ),
                localAccountId = LocalAccountId("local-account"),
                providerAccountId = null,
                fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
                source = QuotaSnapshotSource.ManualRefresh,
            )
        } catch (exception: RuntimeException) {
            fail("mapper should not throw for out of range reset_at: ${exception::class.simpleName}")
            return
        }

        val fiveHour = snapshot.windows[0]
        assertEquals("five_hour", fiveHour.windowId.value)
        assertNull(fiveHour.usedPercent)
        assertNull(fiveHour.resetAt)
        assertNull(fiveHour.limitWindowSeconds)
        assertEquals(QuotaWindowAvailability.DecodeFailed, fiveHour.availability)

        val weekly = snapshot.windows[1]
        assertEquals("weekly", weekly.windowId.value)
        assertEquals(41, weekly.usedPercent)
        assertEquals(Instant.ofEpochSecond(1770500000L), weekly.resetAt)
        assertEquals(604_800, weekly.limitWindowSeconds)
        assertEquals(QuotaWindowAvailability.Available, weekly.availability)
    }

    @Test
    fun `null reset value keeps otherwise valid window available`() {
        val snapshot = mapper.map(
            dto = CodexUsageResponseDto(
                rateLimit = CodexRateLimitDto(
                    primaryWindow = CodexWindowDto(
                        usedPercent = 62,
                        resetAt = null,
                        limitWindowSeconds = 18_000,
                    ),
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
        )

        val fiveHour = snapshot.windows[0]
        assertEquals(62, fiveHour.usedPercent)
        assertNull(fiveHour.resetAt)
        assertEquals(18_000, fiveHour.limitWindowSeconds)
        assertEquals(QuotaWindowAvailability.Available, fiveHour.availability)
    }

    @Test
    fun `maps missing and decode failed windows without synthesized fields`() {
        val snapshot = mapper.map(
            dto = CodexUsageResponseDto(
                rateLimit = CodexRateLimitDto(
                    primaryWindow = null,
                    primaryWindowDecodeFailed = false,
                    secondaryWindow = null,
                    secondaryWindowDecodeFailed = true,
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.BackgroundRefresh,
        )

        val fiveHour = snapshot.windows[0]
        assertEquals("five_hour", fiveHour.windowId.value)
        assertNull(fiveHour.usedPercent)
        assertNull(fiveHour.resetAt)
        assertNull(fiveHour.limitWindowSeconds)
        assertEquals(QuotaWindowAvailability.Missing, fiveHour.availability)

        val weekly = snapshot.windows[1]
        assertEquals("weekly", weekly.windowId.value)
        assertNull(weekly.usedPercent)
        assertNull(weekly.resetAt)
        assertNull(weekly.limitWindowSeconds)
        assertEquals(QuotaWindowAvailability.DecodeFailed, weekly.availability)
    }

    @Test
    fun `maps credits only when required booleans are present`() {
        val snapshot = mapper.map(
            dto = CodexUsageResponseDto(
                credits = CodexCreditsDto(
                    hasCredits = true,
                    unlimited = false,
                    balance = 12.5,
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.DeviceCodeLogin,
        )

        assertEquals(true, snapshot.credits?.hasCredits)
        assertEquals(false, snapshot.credits?.unlimited)
        assertEquals(12.5, snapshot.credits?.balance ?: 0.0, 0.0)
    }

    @Test
    fun `leaves credits null when decode failed or required booleans are missing`() {
        val decodeFailedSnapshot = mapper.map(
            dto = CodexUsageResponseDto(
                credits = CodexCreditsDto(
                    hasCredits = true,
                    unlimited = false,
                    balance = 12.5,
                ),
                creditsDecodeFailed = true,
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.DeviceCodeLogin,
        )
        val missingBooleanSnapshot = mapper.map(
            dto = CodexUsageResponseDto(
                credits = CodexCreditsDto(
                    hasCredits = true,
                    unlimited = null,
                    balance = 12.5,
                ),
            ),
            localAccountId = LocalAccountId("local-account"),
            providerAccountId = null,
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.DeviceCodeLogin,
        )

        assertNull(decodeFailedSnapshot.credits)
        assertNull(missingBooleanSnapshot.credits)
    }
}
