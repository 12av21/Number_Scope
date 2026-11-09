package com.example.data.scanners

import com.example.data.database.FindingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class PlatformTarget(
    val name: String,
    val category: String, // Social, Code, Dev, Media, Community
    val checkUrl: String,
    val profileUrl: String,
    val expectedNotFoundCode: Int = 404,
    val expectedFoundCode: Int = 200
)

data class UsernameScanResult(
    val platform: String,
    val profileUrl: String,
    val exists: Boolean,
    val statusCode: Int,
    val error: String? = null
)

object UsernameOsintEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val platforms = listOf(
        PlatformTarget("GitHub", "Code", "https://api.github.com/users/{u}", "https://github.com/{u}"),
        PlatformTarget("GitLab", "Code", "https://gitlab.com/{u}", "https://gitlab.com/{u}"),
        PlatformTarget("Reddit", "Community", "https://www.reddit.com/user/{u}/about.json", "https://www.reddit.com/user/{u}"),
        PlatformTarget("HackerNews", "Dev", "https://hacker-news.firebaseio.com/v0/user/{u}.json", "https://news.ycombinator.com/user?id={u}"),
        PlatformTarget("Dev.to", "Dev", "https://dev.to/api/users/by_username?url={u}", "https://dev.to/{u}"),
        PlatformTarget("Telegram", "Chat", "https://t.me/{u}", "https://t.me/{u}"),
        PlatformTarget("Medium", "Blogging", "https://medium.com/@{u}", "https://medium.com/@{u}"),
        PlatformTarget("Pinterest", "Media", "https://www.pinterest.com/{u}/", "https://www.pinterest.com/{u}"),
        PlatformTarget("Twitter/X", "Social", "https://x.com/{u}", "https://x.com/{u}"),
        PlatformTarget("Instagram", "Social", "https://www.instagram.com/{u}/", "https://www.instagram.com/{u}"),
        PlatformTarget("DockerHub", "DevOps", "https://hub.docker.com/v2/users/{u}", "https://hub.docker.com/u/{u}"),
        PlatformTarget("NPM", "Code", "https://www.npmjs.com/~{u}", "https://www.npmjs.com/~{u}"),
        PlatformTarget("PyPI", "Code", "https://pypi.org/user/{u}/", "https://pypi.org/user/{u}"),
        PlatformTarget("Vimeo", "Media", "https://vimeo.com/{u}", "https://vimeo.com/{u}"),
        PlatformTarget("Keybase", "Security", "https://keybase.io/{u}", "https://keybase.io/{u}"),
        PlatformTarget("Behance", "Design", "https://www.behance.net/{u}", "https://www.behance.net/{u}"),
        PlatformTarget("Dribbble", "Design", "https://dribbble.com/{u}", "https://dribbble.com/{u}"),
        PlatformTarget("Linktree", "Bio", "https://linktr.ee/{u}", "https://linktr.ee/{u}"),
        PlatformTarget("ProductHunt", "Tech", "https://www.producthunt.com/@{u}", "https://www.producthunt.com/@{u}"),
        PlatformTarget("SoundCloud", "Audio", "https://soundcloud.com/{u}", "https://soundcloud.com/{u}"),
        PlatformTarget("Spotify", "Audio", "https://open.spotify.com/user/{u}", "https://open.spotify.com/user/{u}"),
        PlatformTarget("Steam", "Gaming", "https://steamcommunity.com/id/{u}", "https://steamcommunity.com/id/{u}")
    )

    suspend fun executeInvestigation(
        investigationId: String,
        username: String,
        onProgress: suspend (current: Int, total: Int, currentPlatform: String) -> Unit
    ): Pair<List<UsernameScanResult>, List<FindingEntity>> = coroutineScope {
        val cleanUser = username.trim().removePrefix("@")
        val total = platforms.size
        var completed = 0

        val results = platforms.map { target ->
            async(Dispatchers.IO) {
                val checkUrl = target.checkUrl.replace("{u}", cleanUser)
                val profileUrl = target.profileUrl.replace("{u}", cleanUser)

                val scanResult = try {
                    val request = Request.Builder()
                        .url(checkUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val code = response.code
                    response.close()

                    val exists = when (target.name) {
                        "Reddit" -> code == 200
                        "HackerNews" -> code == 200
                        "GitHub" -> code == 200
                        "Dev.to" -> code == 200
                        "DockerHub" -> code == 200
                        else -> code in 200..302
                    }

                    UsernameScanResult(
                        platform = target.name,
                        profileUrl = profileUrl,
                        exists = exists,
                        statusCode = code
                    )
                } catch (e: Exception) {
                    UsernameScanResult(
                        platform = target.name,
                        profileUrl = profileUrl,
                        exists = false,
                        statusCode = 0,
                        error = e.localizedMessage ?: "Timeout / Network Unreachable"
                    )
                }

                synchronized(this@UsernameOsintEngine) {
                    completed++
                }
                withContext(Dispatchers.Main) {
                    onProgress(completed, total, target.name)
                }

                scanResult
            }
        }.awaitAll()

        val findings = mutableListOf<FindingEntity>()

        // Add discovered profiles
        val discovered = results.filter { it.exists }
        discovered.forEach { res ->
            findings.add(
                FindingEntity(
                    investigationId = investigationId,
                    category = "PROFILE",
                    source = res.platform,
                    title = "Public Profile Discovered on ${res.platform}",
                    description = "Active user handle found under @$cleanUser (HTTP ${res.statusCode}). Public web footprint confirmed.",
                    url = res.profileUrl,
                    confidence = "HIGH",
                    severity = "LOW"
                )
            )
        }

        // Add Google Dork for wider search
        val googleDork = "https://www.google.com/search?q=\"$cleanUser\""
        findings.add(
            FindingEntity(
                investigationId = investigationId,
                category = "DORKS",
                source = "Google Web Index",
                title = "Global Web Mentions Search Dork",
                description = "Indexed search across all forums, code commits, and web archives matching username '$cleanUser'.",
                url = googleDork,
                confidence = "HIGH",
                severity = "INFO"
            )
        )

        Pair(results, findings)
    }
}
