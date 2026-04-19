package com.gitlaunch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

val appLogs = androidx.compose.runtime.snapshots.SnapshotStateList<AppLog>()
val logsEnabled = mutableStateOf(true)

fun addLog(level: String, tag: String, message: String, detail: String? = null) {
    if (!logsEnabled.value) return
    val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
    appLogs.add(0, AppLog(ts, level, tag, message, detail))
    if (appLogs.size > 300) appLogs.removeAt(appLogs.size - 1)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = applicationContext.getSharedPreferences("gl_prefs", Context.MODE_PRIVATE)
            val themeMode = remember { mutableStateOf(prefs.getInt("theme_mode", 0)) }
            val paletteId = remember { mutableStateOf(prefs.getInt("palette_id", AppPalettes.NavyBlue)) }
            val palette   = palettes[paletteId.value] ?: palettes[AppPalettes.NavyBlue]!!
            val colorScheme = when (themeMode.value) {
                1    -> buildLightScheme(palette)
                2    -> buildPureBlackScheme(palette)
                else -> buildDarkScheme(palette)
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GithubLauncherApp(
                        themeMode = themeMode,
                        paletteId = paletteId,
                        onThemeSave = { mode, pid ->
                            prefs.edit().putInt("theme_mode", mode).putInt("palette_id", pid).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GithubLauncherApp(
    themeMode: MutableState<Int>,
    paletteId: MutableState<Int>,
    onThemeSave: (Int, Int) -> Unit
) {
    val ctx   = LocalContext.current
    val prefs = ctx.getSharedPreferences("gl_prefs", Context.MODE_PRIVATE)
    var token by remember { mutableStateOf(prefs.getString("gh_token", null)) }
    var discordToken by remember { mutableStateOf(prefs.getString("discord_token", null)) }

    fun saveToken(t: String)   = prefs.edit().putString("gh_token", t).apply()
    fun saveDiscord(t: String) = prefs.edit().putString("discord_token", t).apply()
    fun clearDiscord()         = prefs.edit().remove("discord_token").apply()

    AnimatedContent(
        targetState = token != null,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "root"
    ) { loggedIn ->
        if (loggedIn) {
            MainScaffold(
                token        = token!!,
                discordToken = discordToken,
                themeMode    = themeMode,
                paletteId    = paletteId,
                onThemeSave  = onThemeSave,
                onLogout     = { token = null; prefs.edit().remove("gh_token").apply() },
                onDiscordLogin  = { dt -> discordToken = dt; saveDiscord(dt) },
                onDiscordLogout = { discordToken = null; clearDiscord() }
            )
        } else {
            LoginScreen(onLogin = { t -> token = t; saveToken(t) })
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf("") }
    var loading    by remember { mutableStateOf(false) }
    var error      by remember { mutableStateOf<String?>(null) }
    var showToken  by remember { mutableStateOf(false) }
    val primary    = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animValue by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "anim"
    )
    val shimmer by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shim"
    )
    val progressAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "prog"
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val bg = Color(0xFF0D1117)
            drawRect(bg)
            listOf(
                Triple(Offset(size.width * 0.15f, size.height * 0.2f), 500f, Color(primary.red, primary.green, primary.blue, 0.06f)),
                Triple(Offset(size.width * 0.8f, size.height * 0.6f), 400f, Color(primary.red, primary.green, primary.blue, 0.04f)),
                Triple(Offset(size.width * 0.5f, size.height * 0.9f), 350f, Color(primary.red, primary.green, primary.blue, 0.05f))
            ).forEach { (center, radius, color) ->
                drawCircle(color = color, radius = radius + animValue * 40f, center = center)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.size(88.dp)
                    .background(primary.copy(0.15f), CircleShape)
                    .border(1.5.dp, primary.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Hub, null, tint = primary, modifier = Modifier.size(44.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text("GitHub Launcher", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE6EDF3))
            Text("Your mobile GitHub workspace", fontSize = 14.sp, color = Color(0xFF8B949E))
            Spacer(Modifier.height(36.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                shape  = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, primary.copy(0.2f))
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sign in with GitHub Token", fontSize = 14.sp, color = Color(0xFF8B949E))
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value         = tokenInput,
                        onValueChange = { tokenInput = it; error = null },
                        modifier      = Modifier.fillMaxWidth(),
                        placeholder   = { Text("ghp_xxxxxxxxxxxx", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        leadingIcon   = { Icon(Icons.Outlined.Key, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon  = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation('•'),
                        singleLine    = true,
                        shape         = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (tokenInput.isNotBlank()) {
                                loading = true; error = null
                                scope.launch {
                                    try {
                                        apiGetUser(tokenInput.trim())
                                        addLog("SUCCESS", "Auth", "Login successful")
                                        onLogin(tokenInput.trim())
                                    } catch (e: Exception) {
                                        error = "Invalid token: ${e.message}"
                                        addLog("ERROR", "Auth", "Login failed: ${e.message}")
                                    }
                                    loading = false
                                }
                            }
                        }),
                        isError = error != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = primary,
                            unfocusedBorderColor = Color(0xFF30363D)
                        )
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Text(error!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (loading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color    = primary,
                                trackColor = primary.copy(0.2f)
                            )
                            Text("Authenticating...", fontSize = 12.sp, color = Color(0xFF8B949E))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.fillMaxWidth().height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(primary)
                                    .clickable {
                                        if (tokenInput.isNotBlank()) {
                                            loading = true; error = null
                                            scope.launch {
                                                try {
                                                    apiGetUser(tokenInput.trim())
                                                    addLog("SUCCESS", "Auth", "Login successful")
                                                    onLogin(tokenInput.trim())
                                                } catch (e: Exception) {
                                                    error = "Invalid token: ${e.message}"
                                                    addLog("ERROR", "Auth", "Login failed: ${e.message}")
                                                }
                                                loading = false
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color.Transparent, Color.White.copy(0.15f), Color.Transparent),
                                                start = Offset(shimmer * 300f + 150f, 0f),
                                                end   = Offset(shimmer * 300f + 300f, 60f)
                                            )
                                        )
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Hub, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Sign in", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new"))) }) {
                        Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                        Text("Create a new token", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("v${com.gitlaunch.BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = Color(0xFF8B949E).copy(0.5f))
            Spacer(Modifier.height(16.dp))
        }
    }
}

enum class MainTab { HOME, NOTIFICATIONS, COPILOT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    token: String, discordToken: String?,
    themeMode: MutableState<Int>, paletteId: MutableState<Int>,
    onThemeSave: (Int, Int) -> Unit,
    onLogout: () -> Unit,
    onDiscordLogin: (String) -> Unit,
    onDiscordLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentTab  by remember { mutableStateOf(MainTab.HOME) }
    var user        by remember { mutableStateOf<GitHubUser?>(null) }
    var repos       by remember { mutableStateOf<List<GitHubRepo>>(emptyList()) }
    var tokenInfo   by remember { mutableStateOf<TokenInfo?>(null) }
    var scopes      by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingUser by remember { mutableStateOf(true) }
    var showLogs    by remember { mutableStateOf(false) }
    var footerClicks by remember { mutableStateOf(0) }

    LaunchedEffect(token) {
        loadingUser = true
        try {
            user      = apiGetUser(token)
            tokenInfo = apiGetTokenMeta(token)
            repos     = apiGetRepos(token)
            scopes    = apiCheckTokenScopes(token)
            addLog("SUCCESS", "Main", "User: ${user?.login} repos=${repos.size}")
        } catch (e: Exception) {
            addLog("ERROR", "Main", "Init failed: ${e.message}")
        }
        loadingUser = false
    }

    if (showLogs) {
        LogsDialog(onClose = { showLogs = false })
    }

    val notifCount = remember { mutableStateOf(0) }
    LaunchedEffect(token) {
        try {
            val notifs = apiGetNotifications(token)
            notifCount.value = notifs.count { it.unread }
        } catch (_: Exception) {}
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME,
                    onClick  = { currentTab = MainTab.HOME },
                    icon     = { Icon(if (currentTab == MainTab.HOME) Icons.Outlined.Home else Icons.Outlined.Home, null) },
                    label    = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.NOTIFICATIONS,
                    onClick  = { currentTab = MainTab.NOTIFICATIONS },
                    icon     = {
                        BadgedBox(badge = { if (notifCount.value > 0) Badge { Text("${notifCount.value}") } }) {
                            Icon(Icons.Outlined.Notifications, null)
                        }
                    },
                    label = { Text("Notifications") }
                )
                if ("copilot" in scopes || scopes.isEmpty()) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.COPILOT,
                        onClick  = { currentTab = MainTab.COPILOT },
                        icon     = { Icon(Icons.Outlined.AutoAwesome, null) },
                        label    = { Text("Copilot") }
                    )
                }
                NavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick  = { currentTab = MainTab.SETTINGS },
                    icon     = { Icon(Icons.Outlined.Settings, null) },
                    label    = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label       = "tab",
            modifier    = Modifier.padding(padding)
        ) { tab ->
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    token        = token,
                    user         = user,
                    repos        = repos,
                    tokenInfo    = tokenInfo,
                    scopes       = scopes,
                    loading      = loadingUser,
                    discordToken = discordToken,
                    footerClicks = footerClicks,
                    onFooterClick= { footerClicks++; if (footerClicks >= 5) { showLogs = true; footerClicks = 0 } },
                    onRefresh    = {
                        scope.launch {
                            loadingUser = true
                            try { user = apiGetUser(token); repos = apiGetRepos(token); scopes = apiCheckTokenScopes(token) } catch (_: Exception) {}
                            loadingUser = false
                        }
                    }
                )
                MainTab.NOTIFICATIONS -> NotificationsScreen(token = token, onUnreadChange = { notifCount.value = it })
                MainTab.COPILOT -> CopilotScreen(token = token)
                MainTab.SETTINGS -> SettingsScreen(
                    token           = token,
                    user            = user,
                    scopes          = scopes,
                    themeMode       = themeMode,
                    paletteId       = paletteId,
                    discordToken    = discordToken,
                    onThemeSave     = onThemeSave,
                    onLogout        = onLogout,
                    onDiscordLogin  = onDiscordLogin,
                    onDiscordLogout = onDiscordLogout,
                    onAbout         = { }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    token: String, user: GitHubUser?, repos: List<GitHubRepo>,
    tokenInfo: TokenInfo?, scopes: Set<String>, loading: Boolean,
    discordToken: String?, footerClicks: Int, onFooterClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var searchQuery   by remember { mutableStateOf("") }
    var selectedRepo  by remember { mutableStateOf<GitHubRepo?>(null) }
    var showPermDialog by remember { mutableStateOf<GitHubRepo?>(null) }

    if (selectedRepo != null) {
        RepoScreen(
            token        = token,
            repo         = selectedRepo!!,
            discordToken = discordToken,
            onBack       = { selectedRepo = null }
        )
        return
    }

    if (showPermDialog != null) {
        val repo = showPermDialog!!
        AlertDialog(
            onDismissRequest = { showPermDialog = null },
            icon = { Icon(Icons.Outlined.Lock, null, tint = primary) },
            title = { Text("Your Permissions", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(repo.fullName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val perms = repo.permissions
                    listOf(
                        "View (pull)"   to (perms?.pull ?: true),
                        "Write (push)"  to (perms?.push ?: false),
                        "Admin"         to (perms?.admin ?: false)
                    ).forEach { (label, granted) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 14.sp)
                            Icon(
                                if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                null,
                                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPermDialog = null }) { Text("Close") } }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(primary.copy(0.12f), MaterialTheme.colorScheme.background)))
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .padding(top = 28.dp)
            ) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(120.dp).height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                            Box(Modifier.width(80.dp).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                        }
                    }
                } else if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, primary.copy(0.4f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column(Modifier.weight(1f)) {
                            Text(user.name ?: user.login, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("@${user.login}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!user.bio.isNullOrBlank()) Text(user.bio, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatChip(user.publicRepos.toString(), "repos")
                        StatChip(user.followers.toString(), "followers")
                        StatChip(user.following.toString(), "following")
                    }
                    if (tokenInfo?.expiresAt != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                            Text("Token expires: ${tokenInfo.expiresAt!!.take(10)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text("Search repositories...") },
                leadingIcon   = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon  = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp)) } },
                singleLine    = true,
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        val filtered = if (searchQuery.isEmpty()) repos else repos.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.description?.contains(searchQuery, ignoreCase = true) == true
        }

        if (loading) {
            items(6) { SkeletonRepoCard() }
        } else {
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.FolderOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                            Text(if (searchQuery.isEmpty()) "No repositories found" else "No results for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { repo ->
                    RepoCard(
                        repo         = repo,
                        onClick      = { selectedRepo = repo },
                        onPermissions= { showPermDialog = repo }
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val t   = rememberInfiniteTransition(label = "fc")
                val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "h")
                val c   = Color.hsv(hue, 0.7f, 0.9f)
                Row(Modifier.clickable { onFooterClick() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, null, tint = c, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(6.dp))
                    Text("By Rhyan57", fontSize = 11.sp, color = c, fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Code, null, tint = c, modifier = Modifier.size(12.dp))
                }
                if (footerClicks in 1..4) Text("${5 - footerClicks}x to open console", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
            }
        }
    }
}

@Composable
fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SkeletonRepoCard() {
    val inf = rememberInfiniteTransition(label = "sk")
    val alpha by inf.animateFloat(0.3f, 0.6f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(160.dp).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha * 0.7f), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun RepoCard(repo: GitHubRepo, onClick: () -> Unit, onPermissions: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.4f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (repo.private) Icons.Outlined.Lock else Icons.Outlined.FolderOpen,
                    null,
                    tint  = if (repo.private) MaterialTheme.colorScheme.error else primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(repo.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (repo.fork) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("fork", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (repo.private) {
                    Box(Modifier.background(MaterialTheme.colorScheme.error.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("private", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(repo.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!repo.language.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(languageColor(repo.language), CircleShape))
                        Text(repo.language, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (repo.stargazersCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.StarBorder, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${repo.stargazersCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.background(primary.copy(0.1f), RoundedCornerShape(6.dp))
                        .border(1.dp, primary.copy(0.25f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onPermissions)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("Permissions", fontSize = 9.sp, color = primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun languageColor(lang: String?): Color = when (lang?.lowercase()) {
    "kotlin"     -> Color(0xFF7F52FF)
    "java"       -> Color(0xFFB07219)
    "javascript" -> Color(0xFFF1E05A)
    "typescript" -> Color(0xFF3178C6)
    "python"     -> Color(0xFF3572A5)
    "go"         -> Color(0xFF00ADD8)
    "rust"       -> Color(0xFFDEA584)
    "c++"        -> Color(0xFFF34B7D)
    "c"          -> Color(0xFF555555)
    "swift"      -> Color(0xFFFF6B35)
    "ruby"       -> Color(0xFF701516)
    "php"        -> Color(0xFF4F5D95)
    "html"       -> Color(0xFFE34C26)
    "css"        -> Color(0xFF563D7C)
    "shell"      -> Color(0xFF89E051)
    "lua"        -> Color(0xFF000080)
    else         -> Color(0xFF8B949E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(token: String, repo: GitHubRepo, discordToken: String?, onBack: () -> Unit) {
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var path   by remember { mutableStateOf("") }
    var contents by remember { mutableStateOf<List<GitHubContent>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }
    var error    by remember { mutableStateOf<String?>(null) }
    var branches by remember { mutableStateOf<List<GitHubBranch>>(emptyList()) }
    var currentBranch by remember { mutableStateOf(repo.defaultBranch) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var commits  by remember { mutableStateOf<List<GitHubCommit>>(emptyList()) }
    var showCommits by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    val breadcrumbs = remember { mutableStateListOf<String>() }

    fun loadPath(p: String) {
        loading = true; error = null
        scope.launch {
            try {
                contents = apiGetContents(token, repo.ownerLogin, repo.name, p)
                addLog("SUCCESS", "Repo", "Loaded ${p.ifEmpty { "root" }} (${contents.size} items)")
            } catch (e: Exception) {
                error = e.message
                addLog("ERROR", "Repo", "Load failed: ${e.message}")
            }
            loading = false
        }
    }

    LaunchedEffect(repo.name) {
        loadPath("")
        branches = apiGetBranches(token, repo.ownerLogin, repo.name)
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            icon = { Icon(Icons.Outlined.Add, null, tint = primary) },
            title = { Text("New File", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFileName, onValueChange = { newFileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("File name") },
                    placeholder = { Text("example.kt") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank()) {
                        val filePath = if (path.isEmpty()) newFileName else "$path/$newFileName"
                        showCreateDialog = false
                        scope.launch {
                            val ok = apiCreateFile(token, repo.ownerLogin, repo.name, filePath, "", "Create $newFileName")
                            if (ok) { loadPath(path); addLog("SUCCESS", "Repo", "Created $newFileName") }
                            newFileName = ""
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false; newFileName = "" }) { Text("Cancel") } }
        )
    }

    if (showBranchSheet) {
        ModalBottomSheet(onDismissRequest = { showBranchSheet = false }) {
            Text("Switch Branch", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(branches) { branch ->
                    ListItem(
                        headlineContent = { Text(branch.name) },
                        leadingContent = { Icon(Icons.Outlined.AccountTree, null, modifier = Modifier.size(18.dp)) },
                        trailingContent = { if (branch.name == currentBranch) Icon(Icons.Outlined.Check, null, tint = primary) },
                        modifier = Modifier.clickable { currentBranch = branch.name; showBranchSheet = false; path = ""; breadcrumbs.clear(); loadPath("") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repo.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (breadcrumbs.isNotEmpty()) {
                            Text(breadcrumbs.joinToString(" / "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (breadcrumbs.isEmpty()) onBack()
                        else {
                            breadcrumbs.removeLastOrNull()
                            path = breadcrumbs.joinToString("/")
                            loadPath(path)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showBranchSheet = true }) {
                        Icon(Icons.Outlined.AccountTree, null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { scope.launch { commits = apiGetCommits(token, repo.ownerLogin, repo.name, currentBranch); showCommits = true } }) {
                        Icon(Icons.Outlined.History, null, modifier = Modifier.size(20.dp))
                    }
                    if (repo.permissions?.push == true) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))) }) {
                        Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.AccountTree, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(currentBranch, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (!repo.language.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(languageColor(repo.language), CircleShape))
                        Text(repo.language, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (showCommits) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showCommits = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) }
                            Text("Recent Commits", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    items(commits) { commit ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                            Column(Modifier.padding(12.dp)) {
                                Text(commit.message, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.background(primary.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                        Text(commit.sha, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = primary)
                                    }
                                    Text(commit.authorName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(commit.authorDate.take(10), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                return@Scaffold
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = primary) }
                error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { loadPath(path) }) { Text("Retry") }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(contents, key = { it.sha + it.path }) { item ->
                        FileListItem(
                            item     = item,
                            canEdit  = repo.permissions?.push == true,
                            onClick  = {
                                if (item.type == "dir") {
                                    breadcrumbs.add(item.name)
                                    path = item.path
                                    loadPath(item.path)
                                } else {
                                    EditorActivity.start(
                                        ctx, token, repo.ownerLogin, repo.name,
                                        item.path, item.name, item.sha, discordToken
                                    )
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(item: GitHubContent, canEdit: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val ext = item.name.substringAfterLast('.', "").lowercase()
    val iconColor = when {
        item.type == "dir" -> MaterialTheme.colorScheme.primary
        ext in setOf("kt", "java") -> Color(0xFF7F52FF)
        ext in setOf("js", "ts")   -> Color(0xFFF1E05A)
        ext == "py"                -> Color(0xFF3572A5)
        ext in setOf("json", "yaml", "yml") -> Color(0xFFFAA61A)
        ext == "md"                -> Color(0xFF8B949E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ListItem(
        headlineContent  = { Text(item.name, fontSize = 14.sp, fontWeight = if (item.type == "dir") FontWeight.Medium else FontWeight.Normal) },
        supportingContent = {
            if (item.type != "dir" && item.size > 0) {
                Text(formatFileSize(item.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            Icon(
                when {
                    item.type == "dir" -> Icons.Outlined.Folder
                    ext in setOf("kt", "java", "js", "ts", "py", "lua", "go", "rs", "cpp", "c") -> Icons.Outlined.Code
                    ext in setOf("json", "yaml", "yml", "toml") -> Icons.Outlined.DataObject
                    ext == "md" -> Icons.Outlined.Description
                    ext in setOf("png", "jpg", "jpeg", "gif", "webp", "svg") -> Icons.Outlined.Image
                    else -> Icons.Outlined.InsertDriveFile
                },
                null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024         -> "$bytes B"
    bytes < 1024 * 1024  -> "${bytes / 1024} KB"
    else                 -> "${bytes / (1024 * 1024)} MB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(token: String, onUnreadChange: (Int) -> Unit) {
    val scope   = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var notifs  by remember { mutableStateOf<List<GitHubNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true; error = null
        try {
            notifs = apiGetNotifications(token)
            onUnreadChange(notifs.count { it.unread })
            addLog("SUCCESS", "Notif", "Loaded ${notifs.size} notifications")
        } catch (e: Exception) {
            error = e.message
            addLog("ERROR", "Notif", "Failed: ${e.message}")
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Outlined.Refresh, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = primary) }
            error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { refreshKey++ }) { Text("Retry") }
                }
            }
            notifs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Text("No notifications", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(notifs, key = { it.id }) { notif ->
                    NotifCard(
                        notif    = notif,
                        onMarkRead = {
                            scope.launch {
                                if (apiMarkNotificationRead(token, notif.id)) {
                                    notifs = notifs.map { if (it.id == notif.id) it.copy(unread = false) else it }
                                    onUnreadChange(notifs.count { it.unread })
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                }
            }
        }
    }
}

@Composable
fun NotifCard(notif: GitHubNotification, onMarkRead: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (notif.unread) Box(Modifier.size(7.dp).background(primary, CircleShape))
                Text(notif.subjectTitle, fontSize = 13.sp, fontWeight = if (notif.unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        supportingContent = {
            Column {
                Text(notif.repoFullName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(notif.reason.replace("_", " "), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(notif.subjectType, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        leadingContent = {
            AsyncImage(model = notif.repoAvatarUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        },
        trailingContent = {
            if (notif.unread) {
                IconButton(onClick = onMarkRead, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DoneAll, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotScreen(token: String) {
    val scope   = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val messages = remember { mutableStateListOf<CopilotMessage>() }
    var input   by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(28.dp).background(primary.copy(0.15f), CircleShape), Alignment.Center) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = primary, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("GitHub Copilot", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("AI Assistant", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { messages.clear() }) { Icon(Icons.Outlined.DeleteSweep, null) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).imePadding(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = input,
                        onValueChange = { input = it },
                        modifier      = Modifier.weight(1f),
                        placeholder   = { Text("Ask Copilot anything...") },
                        maxLines      = 4,
                        shape         = RoundedCornerShape(20.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Box(
                        Modifier.size(48.dp).background(
                            if (input.isNotBlank() && !loading) primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ).clickable {
                            if (input.isNotBlank() && !loading) {
                                val userMsg = input.trim()
                                messages.add(CopilotMessage("user", userMsg))
                                input = ""
                                loading = true
                                scope.launch {
                                    val reply = apiCopilotChat(token, messages.toList())
                                    messages.add(CopilotMessage("assistant", reply))
                                    loading = false
                                    addLog("INFO", "Copilot", "Response received")
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = primary, strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Outlined.Send, null, tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(40.dp)) {
                    Box(Modifier.size(72.dp).background(primary.copy(0.1f), CircleShape), Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = primary, modifier = Modifier.size(36.dp))
                    }
                    Text("GitHub Copilot", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Your AI programming assistant. Ask about code, bugs, architecture, or anything development-related.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                    listOf("Explain this error", "Write a Kotlin data class", "How do I use GitHub Actions?").forEach { suggestion ->
                        Box(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .clickable { input = suggestion }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(suggestion, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            Box(Modifier.size(28.dp).background(primary.copy(0.15f), CircleShape).align(Alignment.Bottom), Alignment.Center) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = primary, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            Modifier.widthIn(max = 280.dp)
                                .background(
                                    if (isUser) primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(
                                        topStart = 16.dp, topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 4.dp,
                                        bottomEnd   = if (isUser) 4.dp  else 16.dp
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(msg.content, fontSize = 14.sp, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp, fontFamily = if (msg.content.contains("```")) FontFamily.Monospace else FontFamily.Default)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    token: String, user: GitHubUser?, scopes: Set<String>,
    themeMode: MutableState<Int>, paletteId: MutableState<Int>,
    discordToken: String?,
    onThemeSave: (Int, Int) -> Unit,
    onLogout: () -> Unit,
    onDiscordLogin: (String) -> Unit,
    onDiscordLogout: () -> Unit,
    onAbout: () -> Unit
) {
    val ctx    = LocalContext.current
    val scope  = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var showAbout   by remember { mutableStateOf(false) }
    var showDiscordRpc by remember { mutableStateOf(false) }
    var showTheme   by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    if (showAbout) { AboutScreen(onBack = { showAbout = false }); return }
    if (showDiscordRpc) { DiscordRpcScreen(discordToken = discordToken, onLogin = onDiscordLogin, onLogout = onDiscordLogout, onBack = { showDiscordRpc = false }); return }
    if (showTheme) { ThemeScreen(themeMode = themeMode, paletteId = paletteId, onSave = onThemeSave, onBack = { showTheme = false }); return }
    if (showProfile && user != null) { EditProfileScreen(token = token, user = user, onBack = { showProfile = false }); return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(primary.copy(0.12f), MaterialTheme.colorScheme.background))).padding(horizontal = 20.dp, vertical = 24.dp).padding(top = 24.dp)) {
                if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(CircleShape).border(2.dp, primary.copy(0.4f), CircleShape), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(user.name ?: user.login, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("@${user.login}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (scopes.isNotEmpty()) {
                                Text("Scopes: ${scopes.take(3).joinToString(", ")}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item { SettingsCategory("Account") }
        item { SettingsItem(Icons.Outlined.Person, "Edit Profile", "Update your GitHub profile", enabled = scopes.contains("user")) { showProfile = true } }
        item { SettingsItem(Icons.Outlined.Key, "Token Scopes", "Current permissions: ${scopes.joinToString(", ").ifEmpty { "unknown" }}", onClick = {}) }
        item { SettingsCategory("Appearance") }
        item { SettingsItem(Icons.Outlined.Palette, "Theme & Colors", "Customize app appearance") { showTheme = true } }
        item { SettingsCategory("Integrations") }
        item {
            SettingsItem(
                Icons.Outlined.Hub,
                "Discord RPC",
                if (discordToken != null) "Connected — Activity shown" else "Show GitHub activity in Discord",
                badge = if (discordToken != null) "ON" else null
            ) { showDiscordRpc = true }
        }
        item { SettingsCategory("About") }
        item { SettingsItem(Icons.Outlined.Info, "About GitHub Launcher", "Version, credits & more") { showAbout = true } }
        item { SettingsItem(Icons.Outlined.OpenInBrowser, "View on GitHub", "Sc-Rhyan57/GithubLauncher") { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sc-Rhyan57/GithubLauncher"))) } }
        item { SettingsCategory("Session") }
        item {
            ListItem(
                headlineContent  = { Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                leadingContent   = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                modifier         = Modifier.clickable(onClick = onLogout)
            )
        }
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        title.uppercase(),
        fontSize  = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color     = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier  = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).padding(top = 8.dp)
    )
}

@Composable
fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, enabled: Boolean = true, badge: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent  = { Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
        supportingContent = { if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent   = { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(22.dp)) },
        trailingContent  = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (badge != null) Box(Modifier.background(MaterialTheme.colorScheme.primary.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(badge, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(themeMode: MutableState<Int>, paletteId: MutableState<Int>, onSave: (Int, Int) -> Unit, onBack: () -> Unit) {
    var localMode    by remember { mutableStateOf(themeMode.value) }
    var localPalette by remember { mutableStateOf(paletteId.value) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme & Colors", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                val samplePalette = palettes[localPalette] ?: palettes[AppPalettes.NavyBlue]!!
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(samplePalette.primary, samplePalette.secondary, samplePalette.tertiary))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(28.dp).background(samplePalette.primary, CircleShape))
                            Box(Modifier.size(28.dp).background(samplePalette.secondary, CircleShape))
                            Box(Modifier.size(28.dp).background(samplePalette.tertiary, CircleShape))
                        }
                    }
                }
            }
            item {
                Text("Theme Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Dark" to 0, "Light" to 1, "AMOLED" to 2).forEach { (label, mode) ->
                        val selected = localMode == mode
                        Box(
                            Modifier.weight(1f).height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(12.dp))
                                .clickable { localMode = mode },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Text("Color Palette", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    palettes.forEach { (id, pal) ->
                        val selected = localPalette == id
                        Box(
                            Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(pal.primary, pal.secondary, pal.tertiary)))
                                .border(3.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { localPalette = id }
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { themeMode.value = localMode; paletteId.value = localPalette; onSave(localMode, localPalette) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Apply Theme", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(token: String, user: GitHubUser, onBack: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var name     by remember { mutableStateOf(user.name ?: "") }
    var bio      by remember { mutableStateOf(user.bio ?: "") }
    var location by remember { mutableStateOf(user.location ?: "") }
    var company  by remember { mutableStateOf(user.company ?: "") }
    var blog     by remember { mutableStateOf(user.blog ?: "") }
    var twitter  by remember { mutableStateOf(user.twitterUsername ?: "") }
    var saving   by remember { mutableStateOf(false) }
    var saved    by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 12.dp), strokeWidth = 2.dp, color = primary)
                    else IconButton(onClick = {
                        saving = true; error = null
                        scope.launch {
                            val ok = apiUpdateProfile(token, name.ifBlank { null }, bio.ifBlank { null }, location.ifBlank { null }, company.ifBlank { null }, blog.ifBlank { null }, twitter.ifBlank { null })
                            if (ok) { saved = true; addLog("SUCCESS", "Profile", "Updated") } else error = "Failed to update profile"
                            saving = false
                        }
                    }) { Icon(if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            item { ProfileField("Name", name) { name = it } }
            item { ProfileField("Bio", bio, maxLines = 4) { bio = it } }
            item { ProfileField("Location", location) { location = it } }
            item { ProfileField("Company", company) { company = it } }
            item { ProfileField("Website / Blog", blog) { blog = it } }
            item { ProfileField("Twitter Username", twitter, leadingText = "@") { twitter = it } }
        }
    }
}

@Composable
fun ProfileField(label: String, value: String, maxLines: Int = 1, leadingText: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        modifier      = Modifier.fillMaxWidth(),
        label         = { Text(label) },
        singleLine    = maxLines == 1,
        maxLines      = maxLines,
        shape         = RoundedCornerShape(12.dp),
        prefix        = if (leadingText != null) ({ Text(leadingText, color = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordRpcScreen(discordToken: String?, onLogin: (String) -> Unit, onLogout: () -> Unit, onBack: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var inputToken   by remember { mutableStateOf("") }
    var showToken    by remember { mutableStateOf(false) }
    var loading      by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf<String?>(null) }
    var showTimeEditing by remember { mutableStateOf(true) }
    var showButtons  by remember { mutableStateOf(true) }
    val ctx          = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(26.dp).background(Color(0xFF5865F2).copy(0.15f), CircleShape), Alignment.Center) {
                            Icon(Icons.Outlined.Hub, null, tint = Color(0xFF5865F2), modifier = Modifier.size(14.dp))
                        }
                        Text("Discord RPC", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF5865F2).copy(0.08f)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF5865F2).copy(0.2f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).background(Color(0xFF5865F2).copy(0.15f), CircleShape), Alignment.Center) {
                                Icon(Icons.Outlined.Hub, null, tint = Color(0xFF5865F2), modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Discord Rich Presence", fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                                Text("Show GitHub Launcher activity in your Discord status", fontSize = 12.sp, color = Color(0xFF8B949E))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("When editing a file, Discord will show:", fontSize = 13.sp, color = Color(0xFF8B949E))
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RpcPreviewLine("Editing", "MyFile.kt")
                            RpcPreviewLine("Workspace:", "MyRepository")
                        }
                    }
                }
            }

            if (discordToken == null) {
                item {
                    Text("Discord Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                item {
                    OutlinedTextField(
                        value         = inputToken,
                        onValueChange = { inputToken = it; error = null },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Discord Token") },
                        placeholder   = { Text("Paste your Discord token", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        trailingIcon  = {
                            Row {
                                IconButton(onClick = { showToken = !showToken }) { Icon(if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, modifier = Modifier.size(18.dp)) }
                            }
                        },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation('•'),
                        singleLine    = true,
                        shape         = RoundedCornerShape(14.dp),
                        isError       = error != null,
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5865F2))
                    )
                    if (error != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(error!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(0.08f)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text("Using your Discord token carries risks. Only use if you understand the implications.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            if (inputToken.isNotBlank()) {
                                loading = true; error = null
                                scope.launch {
                                    try {
                                        val resp = okhttp3.OkHttpClient().newCall(
                                            okhttp3.Request.Builder()
                                                .url("https://discord.com/api/v10/users/@me")
                                                .header("Authorization", inputToken.trim())
                                                .build()
                                        ).execute()
                                        if (resp.isSuccessful) { onLogin(inputToken.trim()); addLog("SUCCESS", "DiscordRpc", "Connected") }
                                        else error = "Invalid Discord token"
                                    } catch (e: Exception) { error = e.message }
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                        enabled = !loading
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else { Icon(Icons.Outlined.Hub, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Connect Discord Account", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Text("Connected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Discord RPC is active. Your activity will be shown while using the editor.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Text("RPC Options", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Show edit duration") },
                                supportingContent = { Text("Display time spent editing", fontSize = 12.sp) },
                                trailingContent = {
                                    Switch(checked = showTimeEditing, onCheckedChange = { showTimeEditing = it }, colors = SwitchDefaults.colors(checkedThumbColor = primary))
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            ListItem(
                                headlineContent = { Text("Show repo button") },
                                supportingContent = { Text("GitHub Launcher button on profile", fontSize = 12.sp) },
                                trailingContent = {
                                    Switch(checked = showButtons, onCheckedChange = { showButtons = it }, colors = SwitchDefaults.colors(checkedThumbColor = primary))
                                }
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick  = { scope.launch(Dispatchers.IO) { try { clearDiscordRpc(discordToken) } catch (_: Exception) {} }; onLogout() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.4f))
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text("Disconnect Discord", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RpcPreviewLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = Color(0xFF8B949E))
        Text(value, fontSize = 12.sp, color = Color(0xFFE6EDF3), fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(0.15f), RoundedCornerShape(16.dp)), Alignment.Center) {
                            Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        }
                        Column {
                            Text("GitHub Launcher", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Box(Modifier.background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("v${com.gitlaunch.BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(model = "https://avatars.githubusercontent.com/u/Sc-Rhyan57", contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape))
                            Column {
                                Text("Rhyan57", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Lead Developer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AboutLinkBtn(Icons.Outlined.Code, "GitHub") { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sc-Rhyan57"))) }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Community", fontWeight = FontWeight.Bold)
                        AboutLinkItem(Icons.Outlined.Code, "Repository", "github.com/Sc-Rhyan57/GithubLauncher") { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sc-Rhyan57/GithubLauncher"))) }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Open Source", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("GNU General Public License v3.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Free software. Use, study, share and improve it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutLinkBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AboutLinkItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.clickable(onClick = onClick).clip(RoundedCornerShape(10.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsDialog(onClose: () -> Unit) {
    var logEnabled by remember { mutableStateOf(logsEnabled.value) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("App Console", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Box(Modifier.background(MaterialTheme.colorScheme.primary.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("${appLogs.size}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null) } },
                    actions = {
                        Text("Logging", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Switch(checked = logEnabled, onCheckedChange = { logEnabled = it; logsEnabled.value = it }, modifier = Modifier.height(24.dp))
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { appLogs.clear() }) { Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (appLogs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                            Text("No logs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
                        items(appLogs) { log ->
                            var expanded by remember { mutableStateOf(false) }
                            val levelColor = when (log.level) { "SUCCESS" -> MaterialTheme.colorScheme.primary; "ERROR" -> MaterialTheme.colorScheme.error; "WARN" -> Color(0xFFFAA61A); else -> MaterialTheme.colorScheme.tertiary }
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(0.5f), RoundedCornerShape(8.dp))
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(log.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.width(5.dp))
                                    Box(Modifier.background(levelColor.copy(0.15f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(log.level, fontSize = 9.sp, color = levelColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(Modifier.width(5.dp))
                                    Text("[${log.tag}]", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                    Text(log.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (expanded && log.detail != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(log.detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
