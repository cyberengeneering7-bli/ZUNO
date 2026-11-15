package com.metrolist.music.zuno

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private data class ZunoAppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String?,
    val notes: String,
    val force: Boolean,
    val minVersionCode: Int,
)

private sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data class Available(val update: ZunoAppUpdate) : UpdateUiState
    data class Downloading(val update: ZunoAppUpdate, val progress: Int) : UpdateUiState
    data class Ready(val update: ZunoAppUpdate, val file: File) : UpdateUiState
    data class Error(val update: ZunoAppUpdate, val message: String) : UpdateUiState
}

object ZunoUpdateManager {
    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()
    private const val PREFS = "zuno_panel"
    private const val CACHED_UPDATE = "latest_app_update"

    private fun cacheDir(c: Context) = File(c.cacheDir, "updates").apply { mkdirs() }

    private fun parseUpdate(o: JSONObject): ZunoAppUpdate? {
        val url = o.optString("apk_url")
        if (!httpsUrl(url) || !url.lowercase().contains(".apk")) return null
        return ZunoAppUpdate(o.optString("version_name"), o.optInt("version_code"), url, o.optString("sha256").takeIf { it.isNotBlank() }, o.optString("notes"), o.optBoolean("force_update"), o.optInt("min_version_code"))
    }

    fun cleanOldApks(c: Context) {
        cacheDir(c).listFiles()?.filter { it.extension == "apk" || it.extension == "tmp" }?.forEach { it.delete() }
    }

    private fun httpsUrl(value: String): Boolean = value.startsWith("https://", true)

    suspend fun fetchLatest(c: Context): ZunoAppUpdate? = withContext(Dispatchers.IO) {
        val conn = runCatching {
            (URL("${ZunoPanelConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/app_updates?select=version_name,version_code,apk_url,sha256,notes,force_update,min_version_code&is_active=eq.true&order=version_code.desc&limit=1").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("apikey", ZunoPanelConfig.SUPABASE_ANON_KEY)
                if (ZunoPanelConfig.SUPABASE_ANON_KEY.startsWith("eyJ")) setRequestProperty("Authorization", "Bearer ${ZunoPanelConfig.SUPABASE_ANON_KEY}")
            }
        }.getOrNull() ?: return@withContext cached(c)
        try {
            if (conn.responseCode !in 200..299) return@withContext cached(c)
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() == 0) return@withContext cached(c)
            val update = parseUpdate(arr.getJSONObject(0)) ?: return@withContext cached(c)
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CACHED_UPDATE, arr.getJSONObject(0).toString()).apply()
            update
        } catch (_: Exception) { cached(c) } finally { conn.disconnect() }
    }

    private fun cached(c: Context): ZunoAppUpdate? = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CACHED_UPDATE, null)?.let { runCatching { parseUpdate(JSONObject(it)) }.getOrNull() }

    suspend fun download(c: Context, update: ZunoAppUpdate): Result<File> = withContext(Dispatchers.IO) {
        val target = File(cacheDir(c), "zuno-${update.versionCode}.apk")
        if (target.isFile && target.length() > 0) {
            val checked = verify(c, update, target)
            if (checked.isFailure) target.delete()
            return@withContext checked
        }
        val tmp = File(cacheDir(c), "zuno-${update.versionCode}.apk.tmp")
        _progress.value = 0
        try {
            var current = update.apkUrl
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = (URL(current).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 10000; readTimeout = 30000 }
                if (conn.responseCode in 300..399) {
                    current = conn.getHeaderField("Location") ?: error("Download redirect has no location")
                    if (!httpsUrl(current) || ++redirects > 5) error("Unsafe download redirect")
                    conn.disconnect()
                } else break
            }
            if (conn.responseCode !in 200..299) error("Download failed (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input -> tmp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer, 0, n); done += n
                    if (total > 0) _progress.value = ((done * 100) / total).toInt().coerceIn(0, 100)
                }
            }}
            conn.disconnect(); if (!tmp.renameTo(target)) error("Could not save APK")
            val checked = verify(c, update, target)
            if (checked.isFailure) error(checked.exceptionOrNull()?.message ?: "APK verification failed")
            checked
        } catch (e: Exception) { tmp.delete(); target.delete(); Result.failure(e) }
    }

    @Suppress("DEPRECATION")
    private fun verify(c: Context, update: ZunoAppUpdate, apk: File): Result<File> = runCatching {
        if (update.sha256 != null) {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(apk).use { input -> val b = ByteArray(64 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(update.sha256, true)) { "SHA-256 verification failed" }
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = c.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("Invalid APK")
        check(archive.packageName == c.packageName) { "This APK is for a different app" }
        val installed = c.packageManager.getPackageInfo(c.packageName, flags)
        val archiveSignatures = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo.apkContentsSigners.map { it.toByteArray().toList() } else archive.signatures.map { it.toByteArray().toList() }
        val installedSignatures = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo.apkContentsSigners.map { it.toByteArray().toList() } else installed.signatures.map { it.toByteArray().toList() }
        check(archiveSignatures.any { it in installedSignatures }) { "APK signing certificate does not match" }
        apk
    }

    fun install(c: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.FileProvider", apk)
        c.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) })
        return true
    }
}

@Composable
fun ZunoUpdateDialog() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Hidden) }
    var dismissedVersion by remember { mutableStateOf<Int?>(null) }
    fun refresh() { scope.launch { ZunoUpdateManager.cleanOldApks(context); val u = ZunoUpdateManager.fetchLatest(context); if (u != null && BuildConfig.VERSION_CODE < u.versionCode && dismissedVersion != u.versionCode) state = UpdateUiState.Available(u) } }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && state !is UpdateUiState.Ready) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val current = state
    if (current !is UpdateUiState.Hidden) {
        val update = when (current) { is UpdateUiState.Available -> current.update; is UpdateUiState.Downloading -> current.update; is UpdateUiState.Ready -> current.update; is UpdateUiState.Error -> current.update; else -> return }
        val force = update.force || BuildConfig.VERSION_CODE < update.minVersionCode
        if (force) BackHandler(enabled = true) {}
        Dialog(onDismissRequest = { if (!force && current !is UpdateUiState.Downloading) { dismissedVersion = update.versionCode; state = UpdateUiState.Hidden } }, properties = DialogProperties(dismissOnBackPress = !force, dismissOnClickOutside = !force)) {
            UpdateCard(current, force, onLater = { dismissedVersion = update.versionCode; state = UpdateUiState.Hidden }, onAction = {
                when (current) {
                    is UpdateUiState.Available, is UpdateUiState.Error -> scope.launch { state = UpdateUiState.Downloading(update, 0); val result = ZunoUpdateManager.download(context, update); state = result.fold({ UpdateUiState.Ready(update, it) }, { UpdateUiState.Error(update, it.message ?: "Download failed") }) }
                    is UpdateUiState.Ready -> if (!ZunoUpdateManager.install(context, current.file)) state = current
                    else -> Unit
                }
            })
        }
    }
}

@Composable
private fun UpdateCard(state: UpdateUiState, force: Boolean, onLater: () -> Unit, onAction: () -> Unit) {
    val update = when (state) { is UpdateUiState.Available -> state.update; is UpdateUiState.Downloading -> state.update; is UpdateUiState.Ready -> state.update; is UpdateUiState.Error -> state.update; else -> return }
    val p = (state as? UpdateUiState.Downloading)?.let { it.progress / 100f } ?: if (state is UpdateUiState.Ready) 1f else 0f
    val animated by animateFloatAsState(p, label = "download-progress")
    val transition = rememberInfiniteTransition(label = "update-blobs")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(8000), RepeatMode.Reverse), label = "blob")
    Box(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF24151B), Color(0xFF121216)))).padding(24.dp)) {
        Box(Modifier.matchParentSize().alpha(.20f).background(Brush.radialGradient(listOf(Color(0xFFFF6B4A), Color.Transparent), radius = 220f, center = androidx.compose.ui.geometry.Offset(120f + drift * 80f, 20f))))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.zuno_logo), "ZUNO logo", Modifier.size(72.dp).clip(CircleShape))
            Text("New version available", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("Latest version ${update.versionName} is ready. Please update to continue.", textAlign = TextAlign.Center)
            if (update.notes.isNotBlank()) LazyColumn(Modifier.height(90.dp)) { item { Text(update.notes, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            when (state) {
                is UpdateUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is UpdateUiState.Downloading -> { Text("Downloading ${state.progress}%"); androidx.compose.material3.LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth()) }
                is UpdateUiState.Ready -> Text("Download complete", color = Color(0xFF7BE495))
                else -> Unit
            }
            Button(onClick = onAction, enabled = state !is UpdateUiState.Downloading, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7048))) {
                Text(when (state) { is UpdateUiState.Ready -> "Install"; is UpdateUiState.Error -> "Retry"; is UpdateUiState.Downloading -> "Downloading…"; else -> "Update now" })
            }
            if (!force && state !is UpdateUiState.Downloading) TextButton(onClick = onLater) { Text("Later") }
        }
    }
}
