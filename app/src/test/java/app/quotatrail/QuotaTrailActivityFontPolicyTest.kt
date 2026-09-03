package app.quotatrail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaTrailActivityFontPolicyTest {
    @Test
    fun `main activity uses geist hybrid font scheme directly`() {
        val source = sourceFile("src/main/java/app/quotatrail/QuotaTrailActivity.kt").readText()

        assertTrue(
            "QuotaTrail should use Geist for UI and Geist Mono for telemetry.",
            source.contains("QuotaTrailTheme(themeMode = themeMode, fontScheme = QuotaTrailFontScheme.GeistHybrid)"),
        )
        assertFalse(
            "Runtime font preference collection must not override the fixed Geist Hybrid scheme.",
            source.contains("fontSchemePreferenceFlow()") || source.contains("toQuotaTrailFontScheme"),
        )
    }

    private fun sourceFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile

        val rootFile = File("app", path)
        if (rootFile.exists()) return rootFile

        error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
