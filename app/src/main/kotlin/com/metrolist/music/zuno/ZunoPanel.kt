/**
 * ZUNO panel client (Supabase REST).
 * Based on Meld / Metrolist (GPL-3.0)
 *
 * Koi extra library nahi: sirf HttpURLConnection + org.json.
 * Sirf anon key use hoti hai. Database ki security RLS rules se hoti hai (schema.sql).
 */

package com.metrolist.music.zuno

import android.content.Context
import android.os.Build
import com.metrolist.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

object ZunoPanelConfig {
    // Supabase -> Project Settings -> API
    const val SUPABASE_URL = "" // jaise: https://abcdxyz.supabase.co
    const val SUPABASE_ANON_KEY = "" // "Publishable key" (sb_publishable_...) ya purani "anon" key. Secret / service_role KABHI mat daalna

    val isConfigured: Boolean
        get() = SUPABASE_URL.startsWith("https://") && SUPABASE_ANON_KEY.length > 20
}

data class ZunoRemoteBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val actionType: String,
    val actionValue: String,
    val isPinned: Boolean,
    val sortOrder: Int,
)

data class ZunoRemoteSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryOrder: Int,
    val showOnHome: Boolean,
    val tags: List<String>,
    val isPinnedBanner: Boolean = false,
    val playCount: Long = 0,
)

data class ZunoSection(
    val name: String,
    val songs: List<ZunoRemoteSong>,
)

data class ZunoAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val linkUrl: String?,
)

data class ZunoAboutLink(
    val label: String,
    val url: String,
    val icon: String,
)

data class ZunoAboutInfo(
    val name: String = "",
    val photoUrl: String = "",
)

data class ZunoGateState(
    val blocked: Boolean = false,
    val blockReason: String = "",
    val maintenanceMessage: String? = null,
    val announcement: ZunoAnnouncement? = null,
)

private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)

object ZunoPanel {
    private const val PREFS = "zuno_panel"
    private const val PENDING_PLAYS_KEY = "pending_song_plays"
    private const val MAX_PENDING_PLAYS = 60
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reported = HashSet<String>()
    private val pendingPlayLock = Any()
    private val pendingPlayFlushMutex = Mutex()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- HTTP
    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
    ): String? =
        withContext(Dispatchers.IO) {
            if (!ZunoPanelConfig.isConfigured) return@withContext null
            var conn: HttpURLConnection? = null
            try {
                val key = ZunoPanelConfig.SUPABASE_ANON_KEY
                conn =
                    (URL(ZunoPanelConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = 8000
                        readTimeout = 10000
                        setRequestProperty("apikey", key)
                        // Naye keys (sb_publishable_...) JWT nahi hote, unhe Authorization mein nahi bhejte.
                        if (key.startsWith("eyJ")) setRequestProperty("Authorization", "Bearer $key")
                        setRequestProperty("Accept", "application/json")
                        if (body != null) {
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        }
                    }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    // ---------------------------------------------------------------- device
    fun deviceId(c: Context): String {
        val p = prefs(c)
        var id = p.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString("device_id", id).apply()
        }
        return id
    }

    // ---------------------------------------------------------------- banners
    private fun inWindow(
        start: String?,
        end: String?,
    ): Boolean =
        try {
            val now = Instant.now()
            (start.isNullOrBlank() || !OffsetDateTime.parse(start).toInstant().isAfter(now)) &&
                (end.isNullOrBlank() || OffsetDateTime.parse(end).toInstant().isAfter(now))
        } catch (e: Exception) {
            true
        }

    private fun parseBanners(raw: String?): List<ZunoRemoteBanner> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ZunoRemoteBanner>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!inWindow(o.str("starts_at"), o.str("ends_at"))) continue
                out +=
                    ZunoRemoteBanner(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        subtitle = o.str("subtitle").orEmpty(),
                        imageUrl = o.str("image_url")?.takeIf { it.isNotBlank() },
                        actionType = o.str("action_type") ?: "none",
                        actionValue = o.str("action_value").orEmpty(),
                        isPinned = o.optBoolean("is_pinned", false),
                        sortOrder = o.optInt("sort_order", 0),
                    )
            }
            out.sortedWith(compareByDescending<ZunoRemoteBanner> { it.isPinned }.thenBy { it.sortOrder })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cachedBanners(c: Context): List<ZunoRemoteBanner> = parseBanners(prefs(c).getString("banners", null))

    suspend fun refreshBanners(c: Context): List<ZunoRemoteBanner>? {
        val raw =
            request(
                "GET",
                "/rest/v1/banners?select=*&is_active=eq.true&order=is_pinned.desc,sort_order.asc,created_at.desc&limit=50",
            ) ?: return null
        prefs(c).edit().putString("banners", raw).apply()
        return parseBanners(raw)
    }

    // ---------------------------------------------------------------- songs
    private fun parseSongs(raw: String?): List<ZunoRemoteSong> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ZunoRemoteSong>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cat = o.optJSONObject("category")
                val catActive = cat?.optBoolean("is_active", true) ?: true
                val tags = ArrayList<String>()
                o.optJSONArray("tags")?.let { t -> for (k in 0 until t.length()) tags += t.optString(k) }
                out +=
                    ZunoRemoteSong(
                        videoId = o.optString("video_id"),
                        title = o.optString("title"),
                        artist = o.str("artist").orEmpty(),
                        coverUrl = o.str("cover_url")?.takeIf { it.isNotBlank() },
                        categoryId = if (cat != null && catActive) cat.str("id") else null,
                        categoryName = if (cat != null && catActive) cat.str("name") else null,
                        categoryOrder = cat?.optInt("sort_order", 0) ?: 0,
                        showOnHome = cat?.optBoolean("show_on_home", true) ?: false,
                        tags = tags,
                        isPinnedBanner = o.optBoolean("is_pinned_banner", false),
                        playCount = o.optLong("play_count", 0),
                    )
            }
            out.filter { it.videoId.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cachedSongs(c: Context): List<ZunoRemoteSong> = parseSongs(prefs(c).getString("songs", null))

    suspend fun refreshSongs(c: Context): List<ZunoRemoteSong>? {
        val raw =
            request(
                "GET",
                "/rest/v1/songs?select=video_id,title,artist,cover_url,tags,sort_order," +
                    "is_pinned_banner,category:categories(id,name,sort_order,show_on_home,is_active)" +
                    "&is_active=eq.true&order=sort_order.asc,created_at.desc&limit=1000",
            ) ?: return null
        val songs = parseSongs(raw)
        prefs(c)
            .edit()
            .putString("songs", raw)
            .putStringSet("song_ids", songs.map { it.videoId }.toSet())
            .apply()
        return songs
    }

    fun cachedTopSongs(c: Context): List<ZunoRemoteSong> = parseSongs(prefs(c).getString("top_songs", null))

    suspend fun refreshTopSongs(c: Context): List<ZunoRemoteSong>? {
        val raw =
            request(
                "POST",
                "/rest/v1/rpc/zuno_top_songs",
                JSONObject().put("p_limit", 20).toString(),
            ) ?: return null
        val songs = parseSongs(raw)
        prefs(c).edit().putString("top_songs", raw).apply()
        return songs
    }

    fun reportSongPlay(
        c: Context,
        videoId: String,
    ) {
        if (!ZunoPanelConfig.isConfigured || videoId.isBlank()) return
        val context = c.applicationContext
        synchronized(pendingPlayLock) {
            val p = prefs(context)
            val queued = JSONArray(p.getString(PENDING_PLAYS_KEY, "[]"))
            if (queued.length() < MAX_PENDING_PLAYS) {
                queued.put(videoId)
                p.edit().putString(PENDING_PLAYS_KEY, queued.toString()).apply()
            }
        }
        retryPendingSongPlays(context)
    }

    fun retryPendingSongPlays(c: Context) {
        val context = c.applicationContext
        scope.launch { flushPendingSongPlays(context) }
    }

    suspend fun flushPendingSongPlays(c: Context) {
        if (!ZunoPanelConfig.isConfigured) return
        pendingPlayFlushMutex.withLock {
            val context = c.applicationContext
            val p = prefs(context)
            val pending =
                synchronized(pendingPlayLock) {
                    val snapshot = JSONArray(p.getString(PENDING_PLAYS_KEY, "[]"))
                    p.edit().putString(PENDING_PLAYS_KEY, "[]").apply()
                    snapshot
                }
            if (pending.length() == 0) return@withLock

            val failed = JSONArray()
            val device = deviceId(context)
            for (start in 0 until pending.length() step 4) {
                val end = minOf(start + 4, pending.length())
                val results =
                    coroutineScope {
                        (start until end).map { i ->
                            async {
                                val videoId = pending.optString(i)
                                if (videoId.isBlank()) return@async null
                                val body = JSONObject().put("p_video_id", videoId).put("p_device_id", device).toString()
                                if (request("POST", "/rest/v1/rpc/record_song_play", body) == null) videoId else null
                            }
                        }.awaitAll()
                    }
                results.filterNotNull().forEach { failed.put(it) }
            }

            if (failed.length() > 0) {
                synchronized(pendingPlayLock) {
                    val newlyQueued = JSONArray(p.getString(PENDING_PLAYS_KEY, "[]"))
                    val combined = JSONArray()
                    for (i in 0 until failed.length()) combined.put(failed.optString(i))
                    val remainingCapacity = (MAX_PENDING_PLAYS - combined.length()).coerceAtLeast(0)
                    for (i in 0 until minOf(newlyQueued.length(), remainingCapacity)) {
                        combined.put(newlyQueued.optString(i))
                    }
                    p.edit().putString(PENDING_PLAYS_KEY, combined.toString()).apply()
                }
            }
        }
    }

    fun sections(songs: List<ZunoRemoteSong>): List<ZunoSection> =
        songs
            .filter { it.categoryName != null && it.showOnHome }
            .groupBy { it.categoryId }
            .values
            .sortedBy { it.first().categoryOrder }
            .map { ZunoSection(it.first().categoryName.orEmpty(), it.take(15)) }

    fun searchLocal(
        songs: List<ZunoRemoteSong>,
        query: String,
        limit: Int = 5,
    ): List<ZunoRemoteSong> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return songs
            .filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.tags.any { t -> t.lowercase().contains(q) }
            }.take(limit)
    }

    // ---------------------------------------------------------------- About page (links + developer)
    private fun parseAboutLinks(raw: String?): List<ZunoAboutLink> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ZunoAboutLink>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.str("url").orEmpty().trim()
                val label = o.str("label").orEmpty().trim()
                if (label.isEmpty() || !(url.startsWith("https://") || url.startsWith("http://"))) continue
                out += ZunoAboutLink(label, url, o.str("icon") ?: "link")
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAboutInfo(raw: String?): ZunoAboutInfo {
        if (raw.isNullOrBlank()) return ZunoAboutInfo()
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return ZunoAboutInfo()
            val v = arr.getJSONObject(0).optJSONObject("value") ?: return ZunoAboutInfo()
            ZunoAboutInfo(
                name = v.optString("name", "").trim(),
                photoUrl = v.optString("photo_url", "").trim().takeIf { it.startsWith("https://") } ?: "",
            )
        } catch (e: Exception) {
            ZunoAboutInfo()
        }
    }

    fun cachedAboutLinks(c: Context): List<ZunoAboutLink> = parseAboutLinks(prefs(c).getString("about_links", null))

    fun cachedAboutInfo(c: Context): ZunoAboutInfo = parseAboutInfo(prefs(c).getString("about_info", null))

    suspend fun refreshAbout(c: Context): Pair<List<ZunoAboutLink>, ZunoAboutInfo>? {
        val rawLinks =
            request(
                "GET",
                "/rest/v1/about_links?select=*&is_active=eq.true&order=sort_order.asc&limit=30",
            ) ?: return null
        val rawInfo = request("GET", "/rest/v1/app_config?select=value&key=eq.about")
        val e = prefs(c).edit().putString("about_links", rawLinks)
        if (rawInfo != null) e.putString("about_info", rawInfo)
        e.apply()
        return parseAboutLinks(rawLinks) to parseAboutInfo(rawInfo)
    }

    // ---------------------------------------------------------------- gate (block / maintenance / announcement)
    suspend fun checkGate(c: Context): ZunoGateState {
        if (!ZunoPanelConfig.isConfigured) return ZunoGateState()
        var blocked = false
        var reason = ""
        try {
            val body =
                JSONObject()
                    .put("p_device_id", deviceId(c))
                    .put("p_app_version", BuildConfig.VERSION_NAME)
                    .put("p_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .toString()
            request("POST", "/rest/v1/rpc/register_device", body)?.let {
                val o = JSONObject(it)
                blocked = o.optBoolean("blocked", false)
                reason = o.optString("reason", "")
            }
        } catch (e: Exception) {
            // network / parse error: block nahi karna
        }
        if (blocked) return ZunoGateState(blocked = true, blockReason = reason)

        var maintenance: String? = null
        try {
            request("GET", "/rest/v1/app_config?select=key,value&key=eq.maintenance")?.let {
                val arr = JSONArray(it)
                if (arr.length() > 0) {
                    val v = arr.getJSONObject(0).optJSONObject("value")
                    if (v != null && v.optBoolean("enabled", false)) {
                        maintenance = v.optString("message", "").ifBlank { "ZUNO maintenance chal raha hai." }
                    }
                }
            }
        } catch (e: Exception) {
        }

        var ann: ZunoAnnouncement? = null
        try {
            request(
                "GET",
                "/rest/v1/announcements?select=*&is_active=eq.true&order=created_at.desc&limit=1",
            )?.let {
                val arr = JSONArray(it)
                if (arr.length() > 0) {
                    val o = arr.getJSONObject(0)
                    val id = o.optString("id")
                    val notExpired = inWindow(null, o.str("expires_at"))
                    val seen = prefs(c).getString("ann_seen", null) == id
                    val showOnce = o.optBoolean("show_once", true)
                    if (notExpired && !(showOnce && seen)) {
                        ann = ZunoAnnouncement(id, o.optString("title"), o.str("body").orEmpty(), o.str("link_url"))
                    }
                }
            }
        } catch (e: Exception) {
        }
        return ZunoGateState(maintenanceMessage = maintenance, announcement = ann)
    }

    fun markAnnouncementSeen(
        c: Context,
        id: String,
    ) {
        prefs(c).edit().putString("ann_seen", id).apply()
    }

    // ---------------------------------------------------------------- broken report
    fun isPanelSong(
        c: Context,
        videoId: String,
    ): Boolean = prefs(c).getStringSet("song_ids", emptySet())?.contains(videoId) == true

    /** Sirf tab bulana hai jab gaana retry ke baad bhi na chale. Ek session mein ek gaana ek hi baar. */
    fun reportBroken(
        c: Context,
        videoId: String,
        error: String?,
    ) {
        if (!ZunoPanelConfig.isConfigured || !isPanelSong(c, videoId)) return
        synchronized(reported) { if (!reported.add(videoId)) return }
        val ctx = c.applicationContext
        scope.launch {
            val body =
                JSONObject()
                    .put("p_video_id", videoId)
                    .put("p_device_id", deviceId(ctx))
                    .put("p_error", error ?: "")
                    .toString()
            request("POST", "/rest/v1/rpc/report_broken", body)
        }
    }
}

/** Home / Search dono ek hi data use karte hain, isliye ek jagah rakha hai. */
object ZunoPanelState {
    val banners = MutableStateFlow<List<ZunoRemoteBanner>>(emptyList())
    val songs = MutableStateFlow<List<ZunoRemoteSong>>(emptyList())
    val topSongs = MutableStateFlow<List<ZunoRemoteSong>>(emptyList())
    val aboutLinks = MutableStateFlow<List<ZunoAboutLink>>(emptyList())
    val aboutInfo = MutableStateFlow(ZunoAboutInfo())
    private var initialised = false

    fun init(c: Context) {
        if (initialised) return
        initialised = true
        banners.value = ZunoPanel.cachedBanners(c)
        songs.value = ZunoPanel.cachedSongs(c)
        topSongs.value = ZunoPanel.cachedTopSongs(c)
        aboutLinks.value = ZunoPanel.cachedAboutLinks(c)
        aboutInfo.value = ZunoPanel.cachedAboutInfo(c)
    }

    suspend fun refreshAbout(c: Context) {
        init(c)
        ZunoPanel.refreshAbout(c)?.let {
            aboutLinks.value = it.first
            aboutInfo.value = it.second
        }
    }

    suspend fun refresh(c: Context) {
        init(c)
        ZunoPanel.retryPendingSongPlays(c)
        ZunoPanel.refreshBanners(c)?.let { banners.value = it }
        ZunoPanel.refreshSongs(c)?.let { songs.value = it }
        ZunoPanel.refreshTopSongs(c)?.let { topSongs.value = it }
    }
}
