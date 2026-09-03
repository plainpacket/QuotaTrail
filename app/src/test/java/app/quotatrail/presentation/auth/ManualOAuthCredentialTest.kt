package app.quotatrail.presentation.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualOAuthCredentialTest {
    @Test
    fun `accepts Claude code hash state format`() {
        assertEquals(
            "authorization-code#expected-state",
            parseManualOAuthCredential("authorization-code#expected-state", "expected-state"),
        )
    }

    @Test
    fun `accepts full callback URL without retaining it`() {
        assertEquals(
            "authorization-code#expected-state",
            parseManualOAuthCredential(
                "https://platform.claude.com/oauth/code/callback?code=authorization-code&state=expected-state",
                "expected-state",
            ),
        )
    }

    @Test
    fun `rejects missing or mismatched state`() {
        assertNull(parseManualOAuthCredential("authorization-code", "expected-state"))
        assertNull(parseManualOAuthCredential("authorization-code#attacker-state", "expected-state"))
        assertNull(parseManualOAuthCredential("#expected-state", "expected-state"))
    }
}
