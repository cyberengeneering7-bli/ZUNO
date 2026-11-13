/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LiquidGlassBlurRadiusKey
import com.metrolist.music.constants.LiquidGlassChromaticAberrationKey
import com.metrolist.music.constants.LiquidGlassDepthEffectKey
import com.metrolist.music.constants.LiquidGlassEnabledKey
import com.metrolist.music.constants.LiquidGlassTextColorKey
import com.metrolist.music.constants.LiquidGlassRefractionAmountKey
import com.metrolist.music.constants.LiquidGlassRefractionHeightKey
import com.metrolist.music.constants.LiquidGlassVibrancyKey
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    compact: Boolean = false,
    hasPlayer: Boolean = false,
    onCompactPlayerClick: () -> Unit = {},
) {
    val liquidGlassEnabled by rememberPreference(LiquidGlassEnabledKey, false)
    if (liquidGlassEnabled) {
        LiquidGlassNavigationBar(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            modifier = modifier,
            pureBlack = pureBlack,
            slimNav = slimNav,
            onSearchLongClick = onSearchLongClick,
            onHomeLongHold = onHomeLongHold,
            compact = compact,
            hasPlayer = hasPlayer,
            onCompactPlayerClick = onCompactPlayerClick,
        )
        return
    }

    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                },
                label = if (!slimNav) {
                    {
                        Text(
                            text = stringResource(screen.titleId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun LiquidGlassNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier,
    pureBlack: Boolean,
    slimNav: Boolean,
    onSearchLongClick: (() -> Unit)?,
    onHomeLongHold: (() -> Unit)?,
    compact: Boolean,
    hasPlayer: Boolean,
    onCompactPlayerClick: () -> Unit,
) {
    val vibrancy by rememberPreference(LiquidGlassVibrancyKey, 1.4f)
    val blurRadius by rememberPreference(LiquidGlassBlurRadiusKey, 61f)
    val refractionHeight by rememberPreference(LiquidGlassRefractionHeightKey, 12f)
    val refractionAmount by rememberPreference(LiquidGlassRefractionAmountKey, 24f)
    val chromaticAberration by rememberPreference(LiquidGlassChromaticAberrationKey, 0.5f)
    val depthEffect by rememberPreference(LiquidGlassDepthEffectKey, 0.2f)
    val (glassTextColorArgb) = rememberPreference(LiquidGlassTextColorKey, -1)
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val foreground = Color(glassTextColorArgb)
    val targetHeight = if (compact) 52.dp else if (slimNav) 52.dp else 64.dp
    val navHeight by animateDpAsState(targetHeight, animationSpec = tween(220), label = "liquidNavHeight")
    val radius = navHeight / 2f
    val baseColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val shape = RoundedCornerShape(radius)

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = if (compact) 20.dp else if (slimNav) 6.dp else 8.dp)
                .height(navHeight)
                .clip(shape)
                .liquidGlassIfAvailable(
                    enabled = true,
                    cornerRadius = radius,
                    refractionHeightDp = refractionHeight,
                    refractionAmountDp = refractionAmount,
                    blurRadiusPx = blurRadius,
                    vibrancy = vibrancy,
                    chromaticAberration = chromaticAberration,
                    depthEffect = depthEffect,
                )
                .background(baseColor.copy(alpha = 0.27f), shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.035f), Color.Transparent),
                    ),
                    shape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.46f), shape)
                .padding(horizontal = if (compact) 6.dp else 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationItems.forEachIndexed { index, screen ->
                if (compact && hasPlayer && index == 2) {
                    CompactGlassPlayer(
                        modifier = Modifier.weight(3f).fillMaxHeight(),
                        foreground = foreground,
                        onOpenPlayer = onCompactPlayerClick,
                    )
                }
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                val currentIsSelected by rememberUpdatedState(isSelected)
                val iconRes = remember(isSelected, screen) {
                    if (isSelected) screen.iconIdActive else screen.iconIdInactive
                }
                val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
                val interactionSource = remember { MutableInteractionSource() }

                if (isSearchItem || isHomeHoldItem) {
                    LaunchedEffect(interactionSource) {
                        var isLongClick = false
                        interactionSource.interactions.collectLatest { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> {
                                    isLongClick = false
                                    delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                    isLongClick = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                                }
                                is PressInteraction.Release -> {
                                    if (!isLongClick) onItemClick(screen, currentIsSelected)
                                }
                                is PressInteraction.Cancel -> isLongClick = false
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(if (!compact && isSelected && !slimNav) 1.55f else 1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) foreground.copy(alpha = 0.18f) else Color.Transparent)
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Tab,
                            onClick = {
                                if (!isSearchItem && !isHomeHoldItem) onItemClick(screen, currentIsSelected)
                            },
                        )
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId),
                        tint = foreground,
                    )
                    if (isSelected && !slimNav && !compact) {
                        Text(
                            text = stringResource(screen.titleId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = foreground,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactGlassPlayer(
    modifier: Modifier,
    foreground: Color,
    onOpenPlayer: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isMuted by playerConnection.isMuted.collectAsState()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    Row(
        modifier = modifier.padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenPlayer),
            contentAlignment = Alignment.Center,
        ) {
            val artwork = mediaMetadata?.thumbnailUrl
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                )
            } else {
                Icon(painterResource(R.drawable.album), contentDescription = null, tint = foreground)
            }
        }
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenPlayer),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata?.title ?: "ZUNO",
                color = foreground,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaMetadata?.artists?.firstOrNull()?.name.orEmpty(),
                color = foreground.copy(alpha = 0.72f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable {
                    if (isGuest) playerConnection.toggleMute() else playerConnection.togglePlayPause()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (isGuest) {
                        if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                    } else if (isPlaying) R.drawable.pause else R.drawable.play,
                ),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
