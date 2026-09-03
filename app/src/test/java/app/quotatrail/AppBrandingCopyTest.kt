package app.quotatrail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppBrandingCopyTest {
    @Test
    fun `user-facing product name is QuotaTrail`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("QuotaTrail", context.getString(R.string.app_name))
        assertEquals("QuotaTrail", context.getString(R.string.notification_status_title))
        assertEquals("QuotaTrail %1\$s", context.getString(R.string.settings_about_version_format))
    }

    @Test
    fun `English user-visible resources contain no previous product branding`() {
        val direct = File("src/main/res/values/strings.xml")
        val strings = (if (direct.exists()) direct else File("app/src/main/res/values/strings.xml")).readText()

        listOf("Codex" + "Meter", "Quota" + "Lens").forEach { previousName ->
            assertFalse(strings.contains(previousName))
        }
    }
}
