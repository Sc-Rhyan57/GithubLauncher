package com.gitlaunch

data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    val bio: String?,
    val avatarUrl: String,
    val email: String?,
    val location: String?,
    val company: String?,
    val publicRepos: Int,
    val followers: Int,
    val following: Int,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val blog: String?,
    val twitterUsername: String?
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val private: Boolean,
    val htmlUrl: String,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val updatedAt: String,
    val defaultBranch: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val permissions: RepoPermissions?,
    val fork: Boolean,
    val openIssuesCount: Int
)

data class RepoPermissions(
    val admin: Boolean,
    val push: Boolean,
    val pull: Boolean
)

data class GitHubContent(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val sha: String,
    val downloadUrl: String?,
    val htmlUrl: String,
    val encoding: String? = null,
    val content: String? = null
)

data class GitHubNotification(
    val id: String,
    val reason: String,
    val unread: Boolean,
    val updatedAt: String,
    val subjectTitle: String,
    val subjectType: String,
    val subjectUrl: String?,
    val repoFullName: String,
    val repoAvatarUrl: String
)

data class CopilotMessage(
    val role: String,
    val content: String
)

data class TokenInfo(
    val token: String,
    val expiresAt: String?,
    val createdAt: String?
)

data class AppLog(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val detail: String? = null
)

data class GitHubBranch(
    val name: String,
    val sha: String
)

data class GitHubCommit(
    val sha: String,
    val message: String,
    val authorName: String,
    val authorDate: String,
    val htmlUrl: String
)
