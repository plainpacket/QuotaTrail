package app.quotatrail.presentation.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuietSignalLayoutPolicyTest {
    @Test
    fun `quota windows are full width rows in one panel`() {
        val source = sourceFile("src/main/java/app/quotatrail/presentation/home/HomeQuotaCards.kt").readText()

        assertFalse(source.contains("chunked(2)"))
        assertTrue(source.contains("HorizontalDivider"))
        assertFalse(source.contains("quota_status_dot_breath"))
    }

    @Test
    fun `top level screens use compact margins and accounts avoid a redundant section label`() {
        val home = sourceFile("src/main/java/app/quotatrail/presentation/home/HomeScreen.kt").readText()
        val accounts = sourceFile("src/main/java/app/quotatrail/presentation/account/AccountScreen.kt").readText()
        val settings = sourceFile("src/main/java/app/quotatrail/presentation/settings/SettingsScreen.kt").readText()

        assertTrue(home.contains("padding(horizontal = TrailSpacing.lg"))
        assertTrue(accounts.contains("padding(horizontal = TrailSpacing.lg"))
        assertTrue(settings.contains("padding(horizontal = TrailSpacing.lg"))
        assertFalse(accounts.contains("SectionLabel(R.string.account_saved_section)"))
    }

    @Test
    fun `usage header gives provider identity and refresh equal visual weight`() {
        val home = sourceFile("src/main/java/app/quotatrail/presentation/home/HomeScreen.kt").readText()

        assertTrue(home.contains("HomeProviderMark"))
        assertTrue(home.contains("QuotaTrailShapes.instrument"))
        assertTrue(home.contains("R.string.home_page_indicator_format"))
    }

    private fun sourceFile(relativePath: String): File {
        val appDirectory = File(System.getProperty("user.dir"))
        return File(appDirectory, relativePath)
    }
}
