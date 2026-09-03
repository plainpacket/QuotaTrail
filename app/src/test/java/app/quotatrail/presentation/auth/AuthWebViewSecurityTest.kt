package app.quotatrail.presentation.auth

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthWebViewSecurityTest {
    @Test
    fun `auth webview disables local resource and mixed content attack surfaces`() {
        val webView = WebView(RuntimeEnvironment.getApplication())

        configureAuthWebView(webView, useSoftwareLayer = false)

        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
        assertFalse(webView.settings.allowFileAccessFromFileURLs)
        assertFalse(webView.settings.allowUniversalAccessFromFileURLs)
        assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
        assertFalse(webView.settings.supportMultipleWindows())
        assertTrue(webView.settings.mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
        assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(webView))
        webView.destroy()
    }

    @Test
    fun `oauth redirect matcher requires the exact origin and path`() {
        val expected = "https://platform.claude.com/oauth/code/callback"

        assertTrue(matchesOAuthRedirect("$expected?code=one&state=two", expected))
        assertFalse(matchesOAuthRedirect("https://platform.claude.com.evil.test/oauth/code/callback?code=x", expected))
        assertFalse(matchesOAuthRedirect("https://platform.claude.com/oauth/code/callback/extra?code=x", expected))
        assertFalse(matchesOAuthRedirect("http://platform.claude.com/oauth/code/callback?code=x", expected))
        assertFalse(matchesOAuthRedirect("https://user@platform.claude.com/oauth/code/callback?code=x", expected))
    }
}
