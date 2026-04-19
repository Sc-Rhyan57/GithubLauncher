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

private val rpcClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

suspend fun sendDiscordRpc(discordToken: String, details: String, state: String) = withContext(Dispatchers.IO) {
    try {
        val activities = JSONArray().put(
            JSONObject()
                .put("type", 0)
                .put("name", "GitHub Launcher")
                .put("details", details.take(128))
                .put("state", state.take(128))
                .put("timestamps", JSONObject().put("start", System.currentTimeMillis()))
                .put("assets", JSONObject()
                    .put("large_image", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png")
                    .put("large_text", "GitHub Launcher")
                )
                .put("buttons", JSONArray().put(
                    JSONObject().put("label", "GitHub Launcher").put("url", "https://github.com/Sc-Rhyan57/GithubLauncher")
                ))
        )
        val body = JSONObject()
            .put("status", "online")
            .put("since", 0)
            .put("activities", activities)
            .put("afk", false)
            .toString()
        rpcClient.newCall(
            Request.Builder()
                .url("https://discord.com/api/v10/users/@me/settings")
                .header("Authorization", discordToken)
                .header("Content-Type", "application/json")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
    } catch (_: Exception) {}
}

suspend fun clearDiscordRpc(discordToken: String) = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject()
            .put("status", "online")
            .put("since", 0)
            .put("activities", JSONArray())
            .put("afk", false)
            .toString()
        rpcClient.newCall(
            Request.Builder()
                .url("https://discord.com/api/v10/users/@me/settings")
                .header("Authorization", discordToken)
                .header("Content-Type", "application/json")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
    } catch (_: Exception) {}
}
