/**
 * ZUNO panel se judi UI: banners, picks, search, gate.
 * Based on Meld / Metrolist (GPL-3.0)
 */

package com.metrolist.music.zuno

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ZunoBanner
import com.metrolist.music.ui.component.ZunoBannerCarousel

private fun ZunoRemoteSong.cover(): String = coverUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

fun playZunoSong(
    context: Context,
    playerConnection: PlayerConnection,
    song: ZunoRemoteSong,
) {
    playerConnection.playQueue(
        YouTubeQueue(
            WatchEndpoint(videoId = song.videoId),
            MediaMetadata(
                id = song.videoId,
                title = song.title,
                artists = listOf(MediaMetadata.Artist(id = null, name = song.artist.ifBlank { "Unknown" })),
                duration = -1,
                thumbnailUrl = song.cover(),
            ),
        ),
    )
    ZunoPanel.reportSongPlay(context, song.videoId)
}

private val bannerPalette =
    listOf(
        Color(0xFFFF7A45) to Color(0xFFEB4060),
        Color(0xFF6A4BFF) to Color(0xFFEB4060),
        Color(0xFF1FB5A5) to Color(0xFF3A5BD9),
        Color(0xFFFFB020) to Color(0xFFE8504A),
    )

/** Home ke upar ke banners. Pehle cache (turant), phir internet se naya. */
@Composable
fun ZunoHomeBanners(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current

    LaunchedEffect(Unit) { ZunoPanelState.refresh(context) }
    val remote by ZunoPanelState.banners.collectAsStateWithLifecycle()
    val topSongs by ZunoPanelState.topSongs.collectAsStateWithLifecycle()

    val banners =
        remember(remote, topSongs) {
            val ordered = buildList {
                addAll(topSongs.filter { it.isPinnedBanner }.map { song ->
                    ZunoRemoteBanner(
                        id = "pinned-song:${song.videoId}",
                        title = song.title,
                        subtitle = song.artist,
                        imageUrl = song.coverUrl ?: "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg",
                        actionType = "song",
                        actionValue = song.videoId,
                        isPinned = true,
                        sortOrder = 0,
                    )
                })
                addAll(remote.filter { it.isPinned })
                addAll(topSongs.filterNot { it.isPinnedBanner }.map { song ->
                    ZunoRemoteBanner(
                        id = "top-song:${song.videoId}",
                        title = song.title,
                        subtitle = song.artist,
                        imageUrl = song.coverUrl ?: "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg",
                        actionType = "song",
                        actionValue = song.videoId,
                        isPinned = false,
                        sortOrder = 0,
                    )
                })
                addAll(remote.filterNot { it.isPinned })
            }.distinctBy { banner ->
                if (banner.actionType == "song") "song:${banner.actionValue}" else "banner:${banner.id}"
            }.take(20)

            ordered.mapIndexed { i, banner ->
                val (c1, c2) = bannerPalette[i % bannerPalette.size]
                ZunoBanner(
                    id = banner.id,
                    title = banner.title,
                    subtitle = banner.subtitle,
                    actionType = banner.actionType,
                    actionValue = banner.actionValue,
                    imageUrl = banner.imageUrl,
                    colorStart = c1,
                    colorEnd = c2,
                )
            }
        }
    if (banners.isEmpty()) return

    ZunoBannerCarousel(
        banners = banners,
        onBannerClick = { b ->
            when (b.actionType) {
                "playlist" -> if (b.actionValue.isNotBlank()) navController.navigate("online_playlist/${b.actionValue}")
                "song" ->
                    if (b.actionValue.isNotBlank() && playerConnection != null) {
                        val s = ZunoPanelState.songs.value.firstOrNull { it.videoId == b.actionValue }
                        playZunoSong(
                            context,
                            playerConnection,
                            s ?: ZunoRemoteSong(b.actionValue, b.title, b.subtitle, b.imageUrl, null, null, 0, false, emptyList()),
                        )
                    }
                "link" -> if (b.actionValue.startsWith("http")) runCatching { uriHandler.openUri(b.actionValue) }
                else -> Unit
            }
        },
        modifier = modifier,
    )
}

/** Panel ki categories Home pe sections ban kar dikhti hain. */
@Composable
fun ZunoPicks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val songs by ZunoPanelState.songs.collectAsStateWithLifecycle()
    val sections = remember(songs) { ZunoPanel.sections(songs) }
    if (sections.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        sections.forEach { section ->
            Text(
                text = section.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(section.songs, key = { it.videoId }) { s ->
                    Column(
                        modifier =
                            Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { playerConnection?.let { playZunoSong(context, it, s) } },
                    ) {
                        AsyncImage(
                            model = s.cover(),
                            contentDescription = s.title,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = s.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Search mein sabse upar ZUNO ke apne gaane (panel ke). */
@Composable
fun ZunoSearchSection(query: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ZunoPanelState.init(context) }
    val songs by ZunoPanelState.songs.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val results = remember(songs, query) { ZunoPanel.searchLocal(songs, query) }
    if (results.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = "ZUNO",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        results.forEach { s ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { playerConnection?.let { playZunoSong(context, it, s) } }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = s.cover(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = s.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * App khulte hi: device register, block check, maintenance message, announcement popup.
 * Internet na ho toh kuch bhi block nahi hota.
 */
@Composable
fun ZunoGate() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var state by remember { mutableStateOf(ZunoGateState()) }
    var maintenanceDismissed by remember { mutableStateOf(false) }
    var announcementDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { state = ZunoPanel.checkGate(context) }

    if (state.blocked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Access blocked") },
            text = { Text(state.blockReason.ifBlank { "This device has been blocked." }) },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finishAffinity() }) { Text("Close app") }
            },
        )
        return
    }

    val maintenance = state.maintenanceMessage
    if (maintenance != null && !maintenanceDismissed) {
        AlertDialog(
            onDismissRequest = { maintenanceDismissed = true },
            title = { Text("Maintenance") },
            text = { Text(maintenance) },
            confirmButton = { TextButton(onClick = { maintenanceDismissed = true }) { Text("OK") } },
        )
        return
    }

    val ann = state.announcement
    if (ann != null && !announcementDismissed) {
        val done = {
            ZunoPanel.markAnnouncementSeen(context, ann.id)
            announcementDismissed = true
        }
        AlertDialog(
            onDismissRequest = done,
            title = { Text(ann.title) },
            text = { Text(ann.body) },
            confirmButton = { TextButton(onClick = done) { Text("OK") } },
            dismissButton =
                if (!ann.linkUrl.isNullOrBlank() && ann.linkUrl.startsWith("http")) {
                    {
                        TextButton(
                            onClick = {
                                runCatching { uriHandler.openUri(ann.linkUrl) }
                                done()
                            },
                        ) { Text("Open") }
                    }
                } else {
                    null
                },
        )
    }
}
