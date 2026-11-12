package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.LiquidGlassBlurRadiusKey
import com.metrolist.music.constants.LiquidGlassChromaticAberrationKey
import com.metrolist.music.constants.LiquidGlassDepthEffectKey
import com.metrolist.music.constants.LiquidGlassEnabledKey
import com.metrolist.music.constants.LiquidGlassRefractionAmountKey
import com.metrolist.music.constants.LiquidGlassRefractionHeightKey
import com.metrolist.music.constants.LiquidGlassVibrancyKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.utils.rememberPreference
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidNavigationSettings(navController: NavController) {
    val (enabled, onEnabledChange) = rememberPreference(LiquidGlassEnabledKey, false)
    val (vibrancy, onVibrancyChange) = rememberPreference(LiquidGlassVibrancyKey, 1.4f)
    val (blurRadius, onBlurRadiusChange) = rememberPreference(LiquidGlassBlurRadiusKey, 61f)
    val (refractionHeight, onRefractionHeightChange) = rememberPreference(LiquidGlassRefractionHeightKey, 12f)
    val (refractionAmount, onRefractionAmountChange) = rememberPreference(LiquidGlassRefractionAmountKey, 24f)
    val (chromaticAberration, onChromaticAberrationChange) = rememberPreference(LiquidGlassChromaticAberrationKey, 0.5f)
    val (depthEffect, onDepthEffectChange) = rememberPreference(LiquidGlassDepthEffectKey, 0.2f)

    Column(
        modifier = Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.liquid_glass_enable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.liquid_glass_enable_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.liquid_glass_effects),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )

        EffectSlider(
            title = stringResource(R.string.liquid_glass_vibrancy),
            description = stringResource(R.string.liquid_glass_vibrancy_description),
            value = vibrancy,
            range = 0.5f..2f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.1f×", vibrancy),
            onValueChange = onVibrancyChange,
        )
        EffectSlider(
            title = stringResource(R.string.liquid_glass_blur_radius),
            description = stringResource(R.string.liquid_glass_blur_radius_description),
            value = blurRadius,
            range = 0f..100f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.0f px", blurRadius),
            onValueChange = onBlurRadiusChange,
        )
        EffectSlider(
            title = stringResource(R.string.liquid_glass_refraction_height),
            description = stringResource(R.string.liquid_glass_refraction_height_description),
            value = refractionHeight,
            range = 1f..48f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.0f dp", refractionHeight),
            onValueChange = onRefractionHeightChange,
        )
        EffectSlider(
            title = stringResource(R.string.liquid_glass_refraction_amount),
            description = stringResource(R.string.liquid_glass_refraction_amount_description),
            value = refractionAmount,
            range = 0f..80f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.0f dp", refractionAmount),
            onValueChange = onRefractionAmountChange,
        )
        EffectSlider(
            title = stringResource(R.string.liquid_glass_chromatic_aberration),
            description = stringResource(R.string.liquid_glass_chromatic_aberration_description),
            value = chromaticAberration,
            range = 0f..1f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.2f", chromaticAberration),
            onValueChange = onChromaticAberrationChange,
        )
        EffectSlider(
            title = stringResource(R.string.liquid_glass_depth_effect),
            description = stringResource(R.string.liquid_glass_depth_effect_description),
            value = depthEffect,
            range = 0f..1f,
            enabled = enabled,
            valueLabel = String.format(Locale.getDefault(), "%.2f", depthEffect),
            onValueChange = onDepthEffectChange,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.liquid_glass_compatibility),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.liquid_navigation_title)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun EffectSlider(
    title: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sliderValue.coerceIn(range.start, range.endInclusive),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = range,
                enabled = enabled,
            )
        }
    }
}
