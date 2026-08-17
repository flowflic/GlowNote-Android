package com.glownote.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class AppUpdateTest {
    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(compareAppVersions("0.0.2", "0.0.1") > 0)
        assertEquals(0, compareAppVersions("v0.0.2", "0.0.2.0"))
        assertTrue(compareAppVersions("1.0.0", "0.9.99") > 0)
    }

    @Test
    fun parsesReleaseApiAndPrefersGlowNoteApkAsset() {
        val info = parseGitHubReleaseJson(
            """
            {
              "tag_name": "v0.0.2",
              "html_url": "https://github.com/flowflic/GlowNote-Android/releases/tag/v0.0.2",
              "assets": [
                {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"},
                {"name":"GlowNote-Android-0.0.2.apk","browser_download_url":"https://example.com/glownote.apk"}
              ]
            }
            """.trimIndent(),
        )

        assertNotNull(info)
        assertEquals("0.0.2", info!!.versionName)
        assertEquals("GlowNote-Android-0.0.2.apk", info.assetName)
        assertEquals("https://example.com/glownote.apk", info.downloadUrl)
    }

    @Test
    fun parsesAtomReleaseAndDerivesDownloadAsset() {
        val info = parseGitHubReleaseAtom(
            """
            <feed>
              <entry>
                <title>GlowNote Android 0.0.2</title>
                <link href="https://github.com/flowflic/GlowNote-Android/releases/tag/v0.0.2" />
              </entry>
            </feed>
            """.trimIndent(),
        )

        assertNotNull(info)
        assertEquals("0.0.2", info!!.versionName)
        assertEquals(
            "https://github.com/flowflic/GlowNote-Android/releases/download/v0.0.2/GlowNote-Android-0.0.2.apk",
            info.downloadUrl,
        )
    }

    @Test
    fun fallsBackToAtomWhenGitHubApiConnectionFails() {
        val atomBody = """
            <feed>
              <entry>
                <link href="https://github.com/flowflic/GlowNote-Android/releases/tag/v0.0.2" />
              </entry>
            </feed>
        """.trimIndent()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (chain.request().url.host == "api.github.com") {
                    throw IOException("connection closed")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(atomBody.toResponseBody("application/atom+xml".toMediaType()))
                    .build()
            }
            .build()

        val info = AppUpdateClient(client).checkForUpdate("0.0.1")

        assertNotNull(info)
        assertEquals("0.0.2", info!!.versionName)
    }
}
