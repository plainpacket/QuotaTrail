package app.quotatrail.foundation.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderHttpClientSecurityTest {
    @Test
    fun `host allowlist blocks an unapproved destination before sending a request`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val client = ProviderHttpClient(allowedHosts = setOf("api.anthropic.com"))

            assertThrows(IOException::class.java) {
                runBlocking { client.get(server.url("/private").toString()) }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `host allowlist permits an approved destination`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val client = ProviderHttpClient(allowedHosts = setOf(server.hostName.lowercase()))

            val response = client.get(server.url("/usage").toString())

            assertEquals(200, response.statusCode)
            assertEquals(1, server.requestCount)
        }
    }
}
