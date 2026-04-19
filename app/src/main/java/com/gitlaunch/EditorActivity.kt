package com.gitlaunch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

class EditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TOKEN    = "extra_token"
        const val EXTRA_OWNER    = "extra_owner"
        const val EXTRA_REPO     = "extra_repo"
        const val EXTRA_PATH     = "extra_path"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_SHA      = "extra_sha"
        const val EXTRA_DISCORD_TOKEN = "extra_discord_token"

        fun start(ctx: Context, token: String, owner: String, repo: String, path: String, fileName: String, sha: String, discordToken: String?) {
            ctx.startActivity(
                Intent(ctx, EditorActivity::class.java)
                    .putExtra(EXTRA_TOKEN, token)
                    .putExtra(EXTRA_OWNER, owner)
                    .putExtra(EXTRA_REPO, repo)
                    .putExtra(EXTRA_PATH, path)
                    .putExtra(EXTRA_FILENAME, fileName)
                    .putExtra(EXTRA_SHA, sha)
                    .putExtra(EXTRA_DISCORD_TOKEN, discordToken)
            )
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val token        = intent.getStringExtra(EXTRA_TOKEN) ?: run { finish(); return }
        val owner        = intent.getStringExtra(EXTRA_OWNER) ?: run { finish(); return }
        val repo         = intent.getStringExtra(EXTRA_REPO) ?: run { finish(); return }
        val path         = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val fileName     = intent.getStringExtra(EXTRA_FILENAME) ?: run { finish(); return }
        val sha          = intent.getStringExtra(EXTRA_SHA) ?: ""
        val discordToken = intent.getStringExtra(EXTRA_DISCORD_TOKEN)

        setContent {
            val prefs    = applicationContext.getSharedPreferences("gl_prefs", Context.MODE_PRIVATE)
            val themeMode   = prefs.getInt("theme_mode", 0)
            val paletteId   = prefs.getInt("palette_id", AppPalettes.NavyBlue)
            val palette     = palettes[paletteId] ?: palettes[AppPalettes.NavyBlue]!!
            val colorScheme = when (themeMode) {
                1    -> buildLightScheme(palette)
                2    -> buildPureBlackScheme(palette)
                else -> buildDarkScheme(palette)
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EditorScreen(
                        token        = token,
                        owner        = owner,
                        repo         = repo,
                        path         = path,
                        fileName     = fileName,
                        initialSha   = sha,
                        discordToken = discordToken,
                        onBack       = { finish() }
                    )
                }
            }
        }
    }
}

private val syntaxKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "import", "package", "return",
    "if", "else", "when", "for", "while", "do", "in", "is", "as", "null", "true", "false",
    "override", "private", "public", "protected", "internal", "suspend", "data", "sealed",
    "abstract", "open", "final", "by", "companion", "init", "constructor", "this", "super",
    "const", "lateinit", "lazy", "defer", "typealias", "throw", "try", "catch", "finally",
    "continue", "break", "it", "apply", "also", "let", "run", "with",
    "def", "async", "await", "static", "void", "int", "string", "bool", "function",
    "export", "import", "from", "require", "module", "extends", "implements",
    "public", "private", "protected", "new", "delete", "typeof", "instanceof"
)

private fun buildAnnotatedCode(text: String, isDark: Boolean): AnnotatedString {
    val keywordColor  = if (isDark) Color(0xFF569CD6) else Color(0xFF0000FF)
    val stringColor   = if (isDark) Color(0xFFCE9178) else Color(0xFFA31515)
    val commentColor  = if (isDark) Color(0xFF6A9955) else Color(0xFF008000)
    val numberColor   = if (isDark) Color(0xFFB5CEA8) else Color(0xFF098658)
    val annotColor    = if (isDark) Color(0xFF4EC9B0) else Color(0xFF267F99)
    val baseColor     = if (isDark) Color(0xFFD4D4D4) else Color(0xFF1F2328)

    return buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor, fontFamily = FontFamily.Monospace)) {
            var i = 0
            while (i < text.length) {
                when {
                    text[i] == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        withStyle(SpanStyle(color = commentColor)) { append(text.substring(i, end)) }
                        i = end
                    }
                    text[i] == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                        withStyle(SpanStyle(color = commentColor)) { append(text.substring(i, end)) }
                        i = end
                    }
                    text[i] == '#' -> {
                        val end = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        withStyle(SpanStyle(color = commentColor)) { append(text.substring(i, end)) }
                        i = end
                    }
                    text[i] == '"' || text[i] == '\'' -> {
                        val quote = text[i]; var j = i + 1
                        while (j < text.length && text[j] != quote && text[j] != '\n') {
                            if (text[j] == '\\') j++; j++
                        }
                        val end = if (j < text.length) j + 1 else j
                        withStyle(SpanStyle(color = stringColor)) { append(text.substring(i, end)) }
                        i = end
                    }
                    text[i] == '@' -> {
                        var j = i + 1
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                        withStyle(SpanStyle(color = annotColor)) { append(text.substring(i, j)) }
                        i = j
                    }
                    text[i].isDigit() -> {
                        var j = i
                        while (j < text.length && (text[j].isDigit() || text[j] == '.' || text[j] == 'L' || text[j] == 'f')) j++
                        withStyle(SpanStyle(color = numberColor)) { append(text.substring(i, j)) }
                        i = j
                    }
                    text[i].isLetter() || text[i] == '_' -> {
                        var j = i
                        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                        val word = text.substring(i, j)
                        if (word in syntaxKeywords) {
                            withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) { append(word) }
                        } else {
                            append(word)
                        }
                        i = j
                    }
                    else -> { append(text[i]); i++ }
                }
            }
        }
    }
}

private fun isSupportedSyntax(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("kt", "java", "js", "ts", "py", "lua", "json", "xml", "html", "css", "sh", "yaml", "yml", "toml", "gradle", "md", "txt", "go", "rs", "cpp", "c", "h", "swift", "rb", "php")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    token: String, owner: String, repo: String, path: String,
    fileName: String, initialSha: String, discordToken: String?, onBack: () -> Unit
) {
    val scope    = rememberCoroutineScope()
    val isDark   = MaterialTheme.colorScheme.background.red < 0.5f
    var content  by remember { mutableStateOf(TextFieldValue("")) }
    var sha      by remember { mutableStateOf(initialSha) }
    var loading  by remember { mutableStateOf(true) }
    var saving   by remember { mutableStateOf(false) }
    var saved    by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf<String?>(null) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var commitMsg by remember { mutableStateOf("Update $fileName") }
    var undoHistory = remember { mutableListOf<TextFieldValue>() }
    var hasChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var rpcActive by remember { mutableStateOf(discordToken != null) }

    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(fileName) {
        loading = true; error = null
        try {
            val (text, fileSha) = apiGetFileContent(token, owner, repo, path)
            content = TextFieldValue(text)
            sha = fileSha
            addLog("SUCCESS", "Editor", "Loaded $fileName (${text.length} chars)")
        } catch (e: Exception) {
            error = e.message
            addLog("ERROR", "Editor", "Load failed: ${e.message}")
        }
        loading = false
        if (discordToken != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    sendDiscordRpc(discordToken, "Editing $fileName", "Workspace: $repo")
                } catch (_: Exception) {}
            }
        }
    }

    BackHandler {
        if (hasChanges) showDiscardDialog = true else onBack()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved changes. Discard them?") },
            confirmButton = {
                Button(onClick = { if (discordToken != null) scope.launch(Dispatchers.IO) { try { clearDiscordRpc(discordToken) } catch (_: Exception) {} }; onBack() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            icon = { Icon(Icons.Outlined.Save, null, tint = primary) },
            title = { Text("Commit Changes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a commit message:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = commitMsg, onValueChange = { commitMsg = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCommitDialog = false
                        saving = true
                        scope.launch {
                            try {
                                val ok = apiUpdateFile(token, owner, repo, path, content.text, sha, commitMsg)
                                if (ok) {
                                    val (newContent, newSha) = apiGetFileContent(token, owner, repo, path)
                                    sha = newSha
                                    hasChanges = false
                                    saved = true
                                    addLog("SUCCESS", "Editor", "Saved $fileName")
                                    delay(2000); saved = false
                                } else {
                                    error = "Failed to save. Check permissions."
                                }
                            } catch (e: Exception) {
                                error = e.message
                                addLog("ERROR", "Editor", "Save failed: ${e.message}")
                            }
                            saving = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) { Text("Commit") }
            },
            dismissButton = { TextButton(onClick = { showCommitDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            if (hasChanges) Box(
                                Modifier.size(7.dp).background(primary, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                        Text("$owner/$repo · $path".take(50), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (hasChanges) showDiscardDialog = true else { if (discordToken != null) scope.launch(Dispatchers.IO) { try { clearDiscordRpc(discordToken) } catch (_: Exception) {} }; onBack() } }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                actions = {
                    if (undoHistory.isNotEmpty()) {
                        IconButton(onClick = {
                            val prev = undoHistory.removeLast()
                            content = prev
                            hasChanges = undoHistory.isNotEmpty()
                        }) { Icon(Icons.AutoMirrored.Outlined.Undo, null, modifier = Modifier.size(20.dp)) }
                    }
                    if (saving) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = primary)
                        }
                    } else {
                        IconButton(
                            onClick = { if (hasChanges) showCommitDialog = true },
                            enabled = hasChanges
                        ) {
                            Icon(
                                if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                                null,
                                tint = if (saved) MaterialTheme.colorScheme.primary else if (hasChanges) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = primary)
                    Text("Loading $fileName...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { error = null; loading = true; scope.launch { try { val (t, s) = apiGetFileContent(token, owner, repo, path); content = TextFieldValue(t); sha = s } catch (e: Exception) { error = e.message }; loading = false } }) { Text("Retry") }
                }
            }
            else -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ext = fileName.substringAfterLast('.', "").uppercase()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (ext.isNotEmpty()) {
                                Box(
                                    Modifier.background(primary.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) { Text(ext, fontSize = 10.sp, color = primary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                            }
                            Text("${content.text.lines().size} lines", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${content.text.length} chars", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (rpcActive && discordToken != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(6.dp).background(Color(0xFF5865F2), androidx.compose.foundation.shape.CircleShape))
                                Text("Discord RPC", fontSize = 9.sp, color = Color(0xFF5865F2), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                            val lines = content.text.lines()
                            Column(
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)).padding(vertical = 8.dp)
                            ) {
                                lines.forEachIndexed { idx, _ ->
                                    Text(
                                        "${idx + 1}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                        modifier = Modifier.padding(horizontal = 10.dp).width(30.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                            if (isSupportedSyntax(fileName)) {
                                BasicTextField(
                                    value = content,
                                    onValueChange = { new ->
                                        if (new.text != content.text) {
                                            undoHistory.add(content)
                                            if (undoHistory.size > 50) undoHistory.removeAt(0)
                                            hasChanges = true
                                            if (discordToken != null) {
                                                scope.launch(Dispatchers.IO) {
                                                    try { sendDiscordRpc(discordToken, "Editing $fileName", "Workspace: $repo") } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        content = new
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    ),
                                    cursorBrush = SolidColor(primary),
                                    decorationBox = { inner ->
                                        if (content.text.isEmpty()) {
                                            Text("Start typing...", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                                        }
                                        Text(
                                            buildAnnotatedCode(content.text, isDark),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 20.sp
                                        )
                                        inner()
                                    }
                                )
                            } else {
                                BasicTextField(
                                    value = content,
                                    onValueChange = { new ->
                                        if (new.text != content.text) { undoHistory.add(content); if (undoHistory.size > 50) undoHistory.removeAt(0); hasChanges = true }
                                        content = new
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
