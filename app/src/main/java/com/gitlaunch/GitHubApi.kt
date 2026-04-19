package com.gitlaunch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.util.Base64

private val apiClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun req(url: String, token: String) =
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()

private fun postReq(url: String, token: String, body: String) =
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

suspend fun apiGetUser(token: String): GitHubUser = withContext(Dispatchers.IO) {
    val resp = apiClient.newCall(req("https://api.github.com/user", token)).execute()
    val body = resp.body?.string() ?: throw Exception("Empty body")
    if (!resp.isSuccessful) throw Exception(JSONObject(body).optString("message", "HTTP ${resp.code}"))
    val j = JSONObject(body)
    GitHubUser(
        login          = j.optString("login"),
        id             = j.optLong("id"),
        name           = j.optString("name").takeIf { it.isNotEmpty() && it != "null" },
        bio            = j.optString("bio").takeIf { it.isNotEmpty() && it != "null" },
        avatarUrl      = j.optString("avatar_url"),
        email          = j.optString("email").takeIf { it.isNotEmpty() && it != "null" },
        location       = j.optString("location").takeIf { it.isNotEmpty() && it != "null" },
        company        = j.optString("company").takeIf { it.isNotEmpty() && it != "null" },
        publicRepos    = j.optInt("public_repos"),
        followers      = j.optInt("followers"),
        following      = j.optInt("following"),
        createdAt      = j.optString("created_at"),
        updatedAt      = j.optString("updated_at"),
        htmlUrl        = j.optString("html_url"),
        blog           = j.optString("blog").takeIf { it.isNotEmpty() && it != "null" },
        twitterUsername= j.optString("twitter_username").takeIf { it.isNotEmpty() && it != "null" }
    )
}

suspend fun apiGetTokenMeta(token: String): TokenInfo = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(
            Request.Builder()
                .url("https://api.github.com/")
                .header("Authorization", "Bearer $token")
                .head()
                .build()
        ).execute()
        val expiry = resp.header("github-authentication-token-expiration")
        TokenInfo(token, expiry, null)
    } catch (_: Exception) {
        TokenInfo(token, null, null)
    }
}

suspend fun apiGetRepos(token: String, page: Int = 1): List<GitHubRepo> = withContext(Dispatchers.IO) {
    val resp = apiClient.newCall(req("https://api.github.com/user/repos?per_page=50&page=$page&sort=updated&affiliation=owner,collaborator,organization_member", token)).execute()
    val body = resp.body?.string() ?: return@withContext emptyList()
    if (!resp.isSuccessful) return@withContext emptyList()
    val arr = JSONArray(body)
    buildList {
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val perms = r.optJSONObject("permissions")
            add(GitHubRepo(
                id             = r.optLong("id"),
                name           = r.optString("name"),
                fullName       = r.optString("full_name"),
                description    = r.optString("description").takeIf { it.isNotEmpty() && it != "null" },
                private        = r.optBoolean("private"),
                htmlUrl        = r.optString("html_url"),
                language       = r.optString("language").takeIf { it.isNotEmpty() && it != "null" },
                stargazersCount= r.optInt("stargazers_count"),
                forksCount     = r.optInt("forks_count"),
                updatedAt      = r.optString("updated_at"),
                defaultBranch  = r.optString("default_branch", "main"),
                ownerLogin     = r.optJSONObject("owner")?.optString("login") ?: "",
                ownerAvatarUrl = r.optJSONObject("owner")?.optString("avatar_url") ?: "",
                permissions    = if (perms != null) RepoPermissions(perms.optBoolean("admin"), perms.optBoolean("push"), perms.optBoolean("pull")) else null,
                fork           = r.optBoolean("fork"),
                openIssuesCount= r.optInt("open_issues_count")
            ))
        }
    }
}

suspend fun apiGetContents(token: String, owner: String, repo: String, path: String = ""): List<GitHubContent> = withContext(Dispatchers.IO) {
    val url = if (path.isEmpty()) "https://api.github.com/repos/$owner/$repo/contents" else "https://api.github.com/repos/$owner/$repo/contents/$path"
    val resp = apiClient.newCall(req(url, token)).execute()
    val body = resp.body?.string() ?: return@withContext emptyList()
    if (!resp.isSuccessful) return@withContext emptyList()
    val arr = try { JSONArray(body) } catch (_: Exception) { return@withContext emptyList() }
    buildList {
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            add(GitHubContent(
                name        = c.optString("name"),
                path        = c.optString("path"),
                type        = c.optString("type"),
                size        = c.optLong("size"),
                sha         = c.optString("sha"),
                downloadUrl = c.optString("download_url").takeIf { it.isNotEmpty() && it != "null" },
                htmlUrl     = c.optString("html_url")
            ))
        }
    }.sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))
}

suspend fun apiGetFileContent(token: String, owner: String, repo: String, path: String): Pair<String, String> = withContext(Dispatchers.IO) {
    val resp = apiClient.newCall(req("https://api.github.com/repos/$owner/$repo/contents/$path", token)).execute()
    val body = resp.body?.string() ?: throw Exception("Empty body")
    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
    val j = JSONObject(body)
    val sha = j.optString("sha")
    val encoded = j.optString("content").replace("\n", "")
    val decoded = try { String(Base64.decode(encoded, Base64.DEFAULT)) } catch (_: Exception) { "" }
    decoded to sha
}

suspend fun apiUpdateFile(token: String, owner: String, repo: String, path: String, content: String, sha: String, message: String): Boolean = withContext(Dispatchers.IO) {
    val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
    val body = JSONObject().put("message", message).put("content", encoded).put("sha", sha).toString()
    val resp = apiClient.newCall(postReq("https://api.github.com/repos/$owner/$repo/contents/$path", token, body)).execute()
    resp.isSuccessful
}

suspend fun apiCreateFile(token: String, owner: String, repo: String, path: String, content: String, message: String): Boolean = withContext(Dispatchers.IO) {
    val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
    val body = JSONObject().put("message", message).put("content", encoded).toString()
    val resp = apiClient.newCall(postReq("https://api.github.com/repos/$owner/$repo/contents/$path", token, body)).execute()
    resp.isSuccessful
}

suspend fun apiDeleteFile(token: String, owner: String, repo: String, path: String, sha: String, message: String): Boolean = withContext(Dispatchers.IO) {
    val body = JSONObject().put("message", message).put("sha", sha).toString()
    val resp = apiClient.newCall(
        Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contents/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .delete(body.toRequestBody("application/json".toMediaType()))
            .build()
    ).execute()
    resp.isSuccessful
}

suspend fun apiGetNotifications(token: String): List<GitHubNotification> = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(req("https://api.github.com/notifications?all=false&per_page=50", token)).execute()
        val body = resp.body?.string() ?: return@withContext emptyList()
        if (!resp.isSuccessful) return@withContext emptyList()
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                val subject = n.optJSONObject("subject")
                val repository = n.optJSONObject("repository")
                add(GitHubNotification(
                    id           = n.optString("id"),
                    reason       = n.optString("reason"),
                    unread       = n.optBoolean("unread"),
                    updatedAt    = n.optString("updated_at"),
                    subjectTitle = subject?.optString("title") ?: "",
                    subjectType  = subject?.optString("type") ?: "",
                    subjectUrl   = subject?.optString("url")?.takeIf { it.isNotEmpty() && it != "null" },
                    repoFullName = repository?.optString("full_name") ?: "",
                    repoAvatarUrl= repository?.optJSONObject("owner")?.optString("avatar_url") ?: ""
                ))
            }
        }
    } catch (_: Exception) { emptyList() }
}

suspend fun apiMarkNotificationRead(token: String, id: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(
            Request.Builder()
                .url("https://api.github.com/notifications/threads/$id")
                .header("Authorization", "Bearer $token")
                .patch("".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        resp.code in 200..205
    } catch (_: Exception) { false }
}

suspend fun apiGetBranches(token: String, owner: String, repo: String): List<GitHubBranch> = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(req("https://api.github.com/repos/$owner/$repo/branches", token)).execute()
        val body = resp.body?.string() ?: return@withContext emptyList()
        if (!resp.isSuccessful) return@withContext emptyList()
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                add(GitHubBranch(b.optString("name"), b.optJSONObject("commit")?.optString("sha") ?: ""))
            }
        }
    } catch (_: Exception) { emptyList() }
}

suspend fun apiGetCommits(token: String, owner: String, repo: String, branch: String): List<GitHubCommit> = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(req("https://api.github.com/repos/$owner/$repo/commits?sha=$branch&per_page=20", token)).execute()
        val body = resp.body?.string() ?: return@withContext emptyList()
        if (!resp.isSuccessful) return@withContext emptyList()
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val commit = c.optJSONObject("commit")
                val author = commit?.optJSONObject("author")
                add(GitHubCommit(
                    sha        = c.optString("sha").take(7),
                    message    = commit?.optString("message")?.lines()?.firstOrNull() ?: "",
                    authorName = author?.optString("name") ?: "",
                    authorDate = author?.optString("date") ?: "",
                    htmlUrl    = c.optString("html_url")
                ))
            }
        }
    } catch (_: Exception) { emptyList() }
}

suspend fun apiUpdateProfile(token: String, name: String?, bio: String?, location: String?, company: String?, blog: String?, twitterUsername: String?): Boolean = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        if (name != null) put("name", name)
        if (bio != null) put("bio", bio)
        if (location != null) put("location", location)
        if (company != null) put("company", company)
        if (blog != null) put("blog", blog)
        if (twitterUsername != null) put("twitter_username", twitterUsername)
    }.toString()
    try {
        val resp = apiClient.newCall(
            Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        resp.isSuccessful
    } catch (_: Exception) { false }
}

suspend fun apiCheckTokenScopes(token: String): Set<String> = withContext(Dispatchers.IO) {
    try {
        val resp = apiClient.newCall(
            Request.Builder().url("https://api.github.com/user").header("Authorization", "Bearer $token").head().build()
        ).execute()
        val scopes = resp.header("X-OAuth-Scopes") ?: return@withContext emptySet()
        scopes.split(",").map { it.trim() }.toSet()
    } catch (_: Exception) { emptySet() }
}

suspend fun apiCopilotChat(token: String, messages: List<CopilotMessage>): String = withContext(Dispatchers.IO) {
    try {
        val msgsArr = JSONArray()
        messages.forEach { m ->
            msgsArr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject()
            .put("model", "gpt-4o")
            .put("messages", msgsArr)
            .put("stream", false)
            .toString()
        val resp = apiClient.newCall(
            Request.Builder()
                .url("https://api.githubcopilot.com/chat/completions")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Editor-Version", "GithubLauncher/1.0")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        val rb = resp.body?.string() ?: return@withContext "Error: empty response"
        if (!resp.isSuccessful) return@withContext "Error: ${JSONObject(rb).optString("message", "HTTP ${resp.code}")}"
        val j = JSONObject(rb)
        j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: "No response"
    } catch (e: Exception) { "Error: ${e.message}" }
}
