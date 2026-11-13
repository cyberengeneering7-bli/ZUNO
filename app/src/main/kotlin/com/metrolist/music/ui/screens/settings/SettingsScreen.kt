/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.constants.LiquidGlassEnabledKey
import com.metrolist.music.constants.LiquidGlassTextColorKey
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow

private data class SettingsEntry(
    val label: String,
    val keywords: String,
    val icon: Painter,
    val route: String,
)

@Composable
private fun SettingsTile(
    entry: SettingsEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                painter = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    val queryState = remember { mutableStateOf("") }
    val query = queryState.value
    val liquidGlassEnabled by com.metrolist.music.utils.rememberPreference(LiquidGlassEnabledKey, false)
    val (glassTextColorArgb) = com.metrolist.music.utils.rememberPreference(LiquidGlassTextColorKey, -1)
    val glassTextColor = Color(glassTextColorArgb)

    val appearanceEntry = SettingsEntry(
        stringResource(R.string.appearance),
        "theme color colour dark light amoled look rang",
        painterResource(R.drawable.palette),
        "settings/appearance",
    )
    val playerEntry = SettingsEntry(
        stringResource(R.string.player_and_audio),
        "sound audio player equalizer volume crossfade quality bass gaana",
        painterResource(R.drawable.play),
        "settings/player",
    )
    val contentEntry = SettingsEntry(
        stringResource(R.string.content),
        "language country region content lyrics",
        painterResource(R.drawable.language),
        "settings/content",
    )
    val aiEntry = SettingsEntry(
        stringResource(R.string.ai_lyrics_translation),
        "ai translate translation lyrics",
        painterResource(R.drawable.translate),
        "settings/ai",
    )
    val autoEntry = if (hasAndroidAuto) {
        SettingsEntry(
            stringResource(R.string.android_auto),
            "car drive android auto",
            painterResource(R.drawable.ic_android_auto),
            "settings/android_auto",
        )
    } else {
        null
    }
    val privacyEntry = SettingsEntry(
        stringResource(R.string.privacy),
        "privacy security history pause",
        painterResource(R.drawable.security),
        "settings/privacy",
    )
    val storageEntry = SettingsEntry(
        stringResource(R.string.storage),
        "storage cache download space clear",
        painterResource(R.drawable.storage),
        "settings/storage",
    )
    val backupEntry = SettingsEntry(
        stringResource(R.string.backup_restore),
        "backup restore export import",
        painterResource(R.drawable.restore),
        "settings/backup_restore",
    )
    val liquidNavigationEntry = SettingsEntry(
        stringResource(R.string.liquid_navigation_title),
        "liquid glass navigation floating bar scroll collapse blur vibrancy refraction effects glass text color",
        painterResource(R.drawable.palette),
        "settings/liquid_navigation",
    )
    val allEntries = listOfNotNull(
        appearanceEntry, playerEntry, contentEntry, aiEntry, autoEntry, liquidNavigationEntry,
        privacyEntry, storageEntry, backupEntry,
    )

    fun entryItem(e: SettingsEntry) = Material3SettingsItem(
        icon = e.icon,
        title = { Text(e.label) },
        onClick = { navController.navigate(e.route) },
    )

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // Settings ke andar search (gaane yahan nahi dhoondhte, sirf options)
        val searchShape = RoundedCornerShape(28.dp)
        val searchGlassModifier = if (liquidGlassEnabled) {
            Modifier
                .clip(searchShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), searchShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), searchShape)
        } else Modifier
        OutlinedTextField(
            value = query,
            onValueChange = { queryState.value = it },
            modifier = Modifier.fillMaxWidth().then(searchGlassModifier),
            singleLine = true,
            shape = searchShape,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (liquidGlassEnabled) glassTextColor else MaterialTheme.colorScheme.onSurface,
            ),
            colors = if (liquidGlassEnabled) {
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.Transparent,
                )
            } else OutlinedTextFieldDefaults.colors(),
            placeholder = {
                Text(
                    "Search settings",
                    color = if (liquidGlassEnabled) glassTextColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = if (liquidGlassEnabled) glassTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { queryState.value = "" }) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            val results = allEntries.filter {
                it.label.lowercase().contains(q) || it.keywords.contains(q)
            }
            if (results.isEmpty()) {
                Text(
                    text = "No settings found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Material3SettingsGroup(
                    title = "Results",
                    items = results.map { entryItem(it) },
                )
            }
        } else {
            // Bade tiles (2 x 2)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsTile(appearanceEntry, { navController.navigate(appearanceEntry.route) }, Modifier.weight(1f))
                SettingsTile(playerEntry, { navController.navigate(playerEntry.route) }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsTile(contentEntry, { navController.navigate(contentEntry.route) }, Modifier.weight(1f))
                SettingsTile(storageEntry, { navController.navigate(storageEntry.route) }, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Material3SettingsGroup(
                title = "Smart",
                items = listOf(entryItem(aiEntry)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (autoEntry != null) {
                Material3SettingsGroup(
                    title = "On the road",
                    items = listOf(entryItem(autoEntry)),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Material3SettingsGroup(
                title = "Safe & backup",
                items = listOf(entryItem(privacyEntry), entryItem(backupEntry)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Material3SettingsGroup(
                title = "ZUNO",
                items = buildList {
                    add(entryItem(liquidNavigationEntry))
                    if (isAndroid12OrLater) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.link),
                                title = { Text(stringResource(R.string.default_links)) },
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                            "package:${context.packageName}".toUri()
                                        )
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        when (e) {
                                            is ActivityNotFoundException -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }

                                            is SecurityException -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }

                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    R.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }
                    if (BuildConfig.UPDATER_AVAILABLE) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.update),
                                title = { Text(stringResource(R.string.updater)) },
                                onClick = { navController.navigate("settings/updater") }
                            )
                        )
                    }
                    val showChangelog = com.metrolist.music.LocalChangelogState.current
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.newspaper),
                            title = { Text(stringResource(R.string.changelog)) },
                            onClick = { showChangelog.value = true }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.info),
                            title = { Text(stringResource(R.string.about)) },
                            onClick = { navController.navigate("settings/about") }
                        )
                    )
                    if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                        val releaseInfo = Updater.getCachedLatestRelease()
                        val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }

                        if (downloadUrl != null) {
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.update),
                                    title = { 
                                        Text(
                                            text = stringResource(R.string.new_version_available),
                                        )
                                    },
                                    description = {
                                        Text(
                                            text = latestVersionName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    showBadge = true,
                                    onClick = { uriHandler.openUri(downloadUrl) }
                                )
                            )
                        }
                    }
                }
            )

            if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                Spacer(modifier = Modifier.height(16.dp))
                ReleaseNotesCard()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
