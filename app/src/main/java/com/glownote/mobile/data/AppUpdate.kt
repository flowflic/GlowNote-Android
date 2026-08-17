package com.glownote.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val assetName: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

data class AppUpdateState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val latest: AppUpdateInfo? = null,
    val message: String = "",
    val statusIsError: Boolean = false,
)

enum class AppInstallResult {
    LAUNCHED_INSTALLER,
    PERMISSION_REQUIRED,
}

private const val GITHUB_API_URL =
    "https://api.github.com/repos/flowflic/GlowNote-Android/releases/latest"
private const val GITHUB_ATOM_URL =
    "https://github.com/flowflic/GlowNote-Android/releases.atom"
private const val GITHUB_RELEASE_BASE_URL =
    "https://github.com/flowflic/GlowNote-Android/releases/download"
private const val GITHUB_RELEASE_PAGE_BASE_URL =
    "https://github.com/flowflic/GlowNote-Android/releases/tag"
private const val DEFAULT_APK_PREFIX = "GlowNote-Android-"

class AppUpdateClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    fun checkForUpdate(currentVersion: String): AppUpdateInfo? {
        val latest = fetchLatestRelease()
        return latest.takeIf { compareAppVersions(it.versionName, currentVersion) > 0 }
    }

    fun downloadApk(info: AppUpdateInfo, cacheDir: File): File {
        val updatesDir = File(cacheDir, "updates").apply {
            if (!exists() && !mkdirs()) {
                error("无法创建更新缓存目录")
            }
        }
        val safeName = safeApkFileName(info.assetName, info.versionName)
        val target = File(updatesDir, safeName)
        val partial = File(updatesDir, "$safeName.part")
        partial.delete()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "GlowNote-Android-Updater")
            .header("Accept", "application/octet-stream")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载更新失败（HTTP ${response.code}）")
            }
            val body = response.body ?: error("更新文件为空")
            body.byteStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (target.exists() && !target.delete()) {
            error("无法替换旧的更新文件")
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    fun launchInstaller(context: Context, apkFile: File): AppInstallResult {
        require(apkFile.isFile) { "更新文件不存在" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return AppInstallResult.PERMISSION_REQUIRED
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return AppInstallResult.LAUNCHED_INSTALLER
    }

    private fun fetchLatestRelease(): AppUpdateInfo {
        val apiResponse = runCatching { requestText(GITHUB_API_URL) }
            .getOrElse { apiError ->
                return fetchAtomRelease(apiError)
            }
        return when {
            apiResponse.code in 200..299 -> {
                parseGitHubReleaseJson(apiResponse.body)
                    ?: error("GitHub Release 数据缺少可安装 APK")
            }
            apiResponse.code == 403 || apiResponse.code == 429 -> {
                fetchAtomRelease()
            }
            else -> error("检查更新失败（HTTP ${apiResponse.code}）")
        }
    }

    private fun fetchAtomRelease(apiError: Throwable? = null): AppUpdateInfo {
        val response = runCatching { requestText(GITHUB_ATOM_URL) }
            .getOrElse { atomError ->
                val detail = if (apiError == null) {
                    atomError.message
                } else {
                    "API: ${apiError.message}; Atom: ${atomError.message}"
                }
                error("GitHub 更新源不可用${detail?.let { "（$it）" } ?: ""}")
            }
        if (response.code !in 200..299) {
            error("GitHub 更新源不可用（HTTP ${response.code}）")
        }
        return parseGitHubReleaseAtom(response.body)
            ?: error("GitHub Release 列表为空")
    }

    private fun requestText(url: String): HttpTextResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GlowNote-Android-Updater")
            .header("Accept", "application/vnd.github+json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            return HttpTextResponse(response.code, response.body?.string().orEmpty())
        }
    }
}

private data class HttpTextResponse(
    val code: Int,
    val body: String,
)

internal fun parseGitHubReleaseJson(body: String): AppUpdateInfo? {
    val root = runCatching { glowJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val tag = root["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
    if (tag.isBlank()) return null
    val version = versionFromTag(tag)
    val releaseUrl = root["html_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: "$GITHUB_RELEASE_PAGE_BASE_URL/$tag"
    val asset = root["assets"]?.jsonArray.orEmpty()
        .mapNotNull { element ->
            val assetObject = element.jsonObject
            val name = assetObject["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val url = assetObject["browser_download_url"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) name to url else null
        }
        .sortedWith(compareByDescending<Pair<String, String>> { it.first.startsWith(DEFAULT_APK_PREFIX) }.thenBy { it.first })
        .firstOrNull()
    val assetName = asset?.first ?: defaultApkName(version)
    val downloadUrl = asset?.second ?: releaseDownloadUrl(tag, assetName)
    return AppUpdateInfo(version, tag, assetName, downloadUrl, releaseUrl)
}

internal fun parseGitHubReleaseAtom(body: String): AppUpdateInfo? {
    val entry = Regex("<entry\\b[^>]*>.*?</entry>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(body)
        ?.value
        ?: return null
    val tag = Regex("releases/tag/([^<\\\"&/]+)", RegexOption.IGNORE_CASE)
        .find(entry)
        ?.groupValues
        ?.get(1)
        ?.trim()
        .orEmpty()
    if (tag.isBlank()) return null
    val version = versionFromTag(tag)
    val assetName = defaultApkName(version)
    return AppUpdateInfo(
        versionName = version,
        tagName = tag,
        assetName = assetName,
        downloadUrl = releaseDownloadUrl(tag, assetName),
        releaseUrl = "$GITHUB_RELEASE_PAGE_BASE_URL/$tag",
    )
}

internal fun compareAppVersions(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    return 0
}

private fun versionFromTag(tag: String): String = tag.trim().removePrefix("v").ifBlank { "0.0.0" }

private fun versionParts(value: String): List<Int> {
    val version = Regex("\\d+(?:\\.\\d+)*").find(value)?.value ?: "0"
    return version.split('.').map { it.toIntOrNull() ?: 0 }
}

private fun defaultApkName(version: String): String = "$DEFAULT_APK_PREFIX$version.apk"

private fun releaseDownloadUrl(tag: String, assetName: String): String =
    "$GITHUB_RELEASE_BASE_URL/${tag.trim('/')}/$assetName"

private fun safeApkFileName(name: String, version: String): String {
    val candidate = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
    return candidate.takeIf { it.endsWith(".apk", ignoreCase = true) && it != ".apk" }
        ?: defaultApkName(version)
}
