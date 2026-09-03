package app.quotatrail.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconResourcePolicyTest {
    @Test
    fun `adaptive launcher icons use the shared route node mark and themed monochrome layer`() {
        val launcher = resource("mipmap-anydpi-v26/ic_launcher.xml").readText()
        val roundLauncher = resource("mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val themedLauncher = resource("mipmap-anydpi-v33/ic_launcher.xml").readText()
        val themedRoundLauncher = resource("mipmap-anydpi-v33/ic_launcher_round.xml").readText()
        val foreground = resource("drawable/ic_launcher_foreground.xml").readText()
        val monochrome = resource("drawable/ic_launcher_monochrome.xml").readText()

        listOf(launcher, roundLauncher, themedLauncher, themedRoundLauncher).forEach { adaptiveIcon ->
            assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_foreground"))
        }
        listOf(themedLauncher, themedRoundLauncher).forEach { adaptiveIcon ->
            assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_monochrome"))
        }
        assertTrue(foreground.contains("android:viewportWidth=\"108\""))
        assertTrue(foreground.contains("#FFFFB86A"))
        assertTrue(foreground.contains("strokeLineCap=\"square\""))
        assertTrue(monochrome.contains("#FFFFFFFF"))
    }

    @Test
    fun `notification icon is a simple monochrome route rather than an alert symbol`() {
        val icon = resource("drawable/ic_notification_status.xml").readText()

        assertTrue(icon.contains("#FFFFFFFF"))
        assertTrue(icon.contains("strokeLineCap=\"square\""))
        assertFalse(icon.contains("M13,17h-2v-2"))
    }

    private fun resource(path: String): File =
        File(System.getProperty("user.dir"), "src/main/res/$path")
}
