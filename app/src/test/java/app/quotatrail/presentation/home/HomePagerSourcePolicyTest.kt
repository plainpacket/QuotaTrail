package app.quotatrail.presentation.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePagerSourcePolicyTest {
    @Test
    fun `home renders account dashboards with a horizontal pager`() {
        val source = sourceFile("src/main/java/app/quotatrail/presentation/home/HomeScreen.kt").readText()

        assertTrue(source.contains("HorizontalPager("))
        assertTrue(source.contains("rememberPagerState("))
        assertTrue(source.contains("onAccountPageSelected"))
    }

    private fun sourceFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile

        val rootFile = File("app", path)
        if (rootFile.exists()) return rootFile

        error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
