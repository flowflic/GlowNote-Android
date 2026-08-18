package com.glownote.mobile.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    @Test
    fun acceptsTeraCloudMkcolSuccessResponse() {
        assertTrue(isMkcolSuccessStatus(200))
        assertTrue(isMkcolSuccessStatus(201))
        assertTrue(isMkcolSuccessStatus(204))
        assertTrue(isMkcolSuccessStatus(301))
        assertTrue(isMkcolSuccessStatus(405))
    }

    @Test
    fun rejectsMkcolErrorResponses() {
        assertFalse(isMkcolSuccessStatus(401))
        assertFalse(isMkcolSuccessStatus(403))
        assertFalse(isMkcolSuccessStatus(500))
    }

    @Test
    fun conditionalReadUsesLocalCacheWhenRemoteReturnsNotModified() = runBlocking {
        var receivedEtag = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                receivedEtag = chain.request().header("If-None-Match").orEmpty()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(304)
                    .message("Not Modified")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val webDav = WebDavClient(client)
        val settings = AppSettings(
            webdav = WebDavSettings(
                enabled = true,
                url = "https://dav.example.com",
                username = "reader",
                path = "/highlight-extension/sync.json",
            ),
        )
        val cache = WebDavCache(
            endpointKey = webDav.cacheKey(settings),
            etag = "\"snapshot-v4\"",
            revision = 4,
            recordCount = 7,
        )

        val result = webDav.readSnapshot(settings, cache)

        assertTrue(result.notModified)
        assertEquals("\"snapshot-v4\"", receivedEtag)
        assertEquals(4, result.snapshot.revision)
        assertEquals(7, cache.recordCount)
    }
}
