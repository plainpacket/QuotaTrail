package app.quotatrail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class QuotaTrailActivityLocalePolicyTest {
    @Test
    fun `main activity does not apply platform locale from compose language state`() {
        val source = sourceFile("src/main/java/app/quotatrail/QuotaTrailActivity.kt").readText()

        assertFalse(
            "Applying LocaleManager from Compose state can recreate QuotaTrailActivity before DataStore emits " +
                "the saved preference, causing a System-target locale loop.",
            source.contains("LaunchedEffect(languagePreference)"),
        )
        assertFalse(
            "QuotaTrailActivity must not collect the old in-app language override; QuotaTrail follows the " +
                "system language now.",
            source.contains("languagePreferenceFlow()"),
        )
        assertFalse(
            "QuotaTrailActivity should use the normal Activity context; explicit in-app locale overrides were " +
                "removed.",
            source.contains("QuotaTrailLocalizedContent("),
        )
        assertFalse(
            "QuotaTrailActivity should not apply app locales directly.",
            source.contains("AppLocaleController.applyToApp(this@QuotaTrailActivity"),
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
