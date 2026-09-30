package com.example.focus_app.data.update

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppUpdateClientTest {
    private fun manifest(code: String = "3", url: String = "https://brucehere.com/downloads/huishen/") =
        """{"applicationId":"com.example.focus_app","versionCode":$code,"versionName":"1.0.2",
            "downloadPageUrl":"$url","releaseNotes":"修复问题\\n改善体验","minSdk":26}"""

    @Test fun reads_release_metadata_and_preserves_version_code() {
        val release = parseAppRelease(manifest())
        assertEquals(3L, release.versionCode)
        assertEquals("1.0.2", release.versionName)
        assertEquals("https://brucehere.com/downloads/huishen/", release.downloadPageUrl)
        assertEquals(26, release.minSdk)
    }

    @Test fun rejects_missing_fractional_negative_and_string_version_codes() {
        for (code in listOf("3.5", "-1", "0", "\"3\"", "null", "2100000001")) {
            assertTrue("Invalid version code $code", runCatching { parseAppRelease(manifest(code)) }.isFailure)
        }
        assertTrue(runCatching { parseAppRelease("{}") }.isFailure)
        assertTrue(runCatching { parseAppRelease("<html>website</html>") }.isFailure)
    }

    @Test fun only_opens_https_pages_on_the_official_host() {
        for (url in listOf("http://brucehere.com/app", "https://brucehere.com.evil.test/app",
            "https://evil.test/app", "javascript:alert(1)", "https://user@brucehere.com/app",
            "https://brucehere.com:8443/app", "/downloads/huishen/")) {
            assertTrue(url, runCatching { parseAppRelease(manifest(url = url)) }.isFailure)
        }
    }

    @Test fun fetches_only_the_manifest_and_revalidates_the_response() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(manifest()))
            val release = AppUpdateClient(server.url("/latest.json").toString()).fetch()
            assertEquals(3L, release.versionCode)
            val request = server.takeRequest()
            assertEquals("/latest.json", request.path)
            assertEquals("no-cache", request.getHeader("Cache-Control"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun rejects_http_errors_spa_fallbacks_and_redirects() = runTest {
        for (response in listOf(
            MockResponse().setResponseCode(404),
            MockResponse().setHeader("Content-Type", "text/html").setBody("<html>SPA</html>"),
            MockResponse().setResponseCode(302).setHeader("Location", "https://brucehere.com/")
        )) {
            MockWebServer().use { server ->
                server.enqueue(response)
                assertTrue(runCatching { AppUpdateClient(server.url("/latest.json").toString()).fetch() }.isFailure)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun rejects_an_unbounded_manifest_response() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(" ".repeat(70_000)))
            assertTrue(runCatching { AppUpdateClient(server.url("/latest.json").toString()).fetch() }.isFailure)
        }
    }

    @Test fun cancelling_the_request_does_not_wait_for_the_network_timeout() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val result = async { AppUpdateClient(server.url("/latest.json").toString()).fetch() }
            runCurrent()
            result.cancelAndJoin()
            assertTrue(result.isCancelled)
        }
    }
}
