package app.quotatrail

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class QuotaTrailApplicationLocalePolicyTest {
    @Test
    fun `application startup clears persisted override and forces the personal build to English`() {
        val source = sourceFile("src/main/java/app/quotatrail/QuotaTrailApplication.kt").readText()

        assertFalse(
            "QuotaTrail is English-only; startup must not re-apply old persisted " +
                "in-app language overrides.",
            source.contains("AppLocaleController.applyToApp"),
        )
        assertFalse(
            "Startup should not read the old language preference just to mutate app locales.",
            source.contains("languagePreferences.languagePreference()"),
        )
        org.junit.Assert.assertTrue(
            "Startup must clear any LocaleManager applicationLocales value left by older builds.",
            source.contains("AppLocaleController.ensureEnglishLocale(this)"),
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
