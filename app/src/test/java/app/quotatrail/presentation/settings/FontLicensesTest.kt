package app.quotatrail.presentation.settings

import app.quotatrail.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FontLicensesTest {
    private fun packagedNotices(): String = RuntimeEnvironment.getApplication().resources
        .openRawResource(R.raw.font_licenses)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun `packaged notices cover every bundled font and copyright holder`() {
        val notices = packagedNotices()
        val fontDirectory = File("src/main/res/font").takeIf { it.isDirectory }
            ?: File("app/src/main/res/font")
        val fonts = fontDirectory.listFiles()?.filter { it.extension == "ttf" }.orEmpty()
        assertEquals(4, fonts.size)
        fonts.forEach { font ->
            assertTrue("Missing notice for ${font.name}", notices.contains(font.name))
        }
        listOf(
            "Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font)",
            "Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font.git)",
            "Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter)",
            "Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)",
        ).forEach { copyright -> assertTrue(notices.contains(copyright)) }
    }

    @Test
    fun `full license is readable offline including all conditions and disclaimer`() {
        val notices = packagedNotices()
        assertTrue(notices.contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))
        listOf("PREAMBLE", "DEFINITIONS", "TERMINATION", "DISCLAIMER").forEach {
            assertTrue(notices.contains(it))
        }
        (1..5).forEach { assertTrue(notices.contains("$it) ")) }
        assertTrue(notices.trimEnd().endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."))
    }

    @Test
    fun `license entry and dismissal have English labels`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Font licenses", context.getString(R.string.settings_font_licenses))
        assertEquals("Close", context.getString(R.string.settings_font_licenses_close))
    }
}
