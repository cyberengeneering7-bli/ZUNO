package com.metrolist.music.ui.screens.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton

private data class LauncherIconOption(
    val componentClassName: String,
    val drawableRes: Int,
    val labelRes: Int,
)

private const val DEFAULT_LAUNCHER_ALIAS = "com.metrolist.music.MainActivityAlias"

private val launcherIconOptions = listOf(
    LauncherIconOption(DEFAULT_LAUNCHER_ALIAS, R.drawable.launcher_icon_00, R.string.launcher_icon_name_0),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias01", R.drawable.launcher_icon_01, R.string.launcher_icon_name_1),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias02", R.drawable.launcher_icon_02, R.string.launcher_icon_name_2),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias03", R.drawable.launcher_icon_03, R.string.launcher_icon_name_3),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias04", R.drawable.launcher_icon_04, R.string.launcher_icon_name_4),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias05", R.drawable.launcher_icon_05, R.string.launcher_icon_name_5),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias06", R.drawable.launcher_icon_06, R.string.launcher_icon_name_6),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias07", R.drawable.launcher_icon_07, R.string.launcher_icon_name_7),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias08", R.drawable.launcher_icon_08, R.string.launcher_icon_name_8),
    LauncherIconOption("com.metrolist.music.LauncherIconAlias09", R.drawable.launcher_icon_09, R.string.launcher_icon_name_9),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherIconSettings(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedIconIndex by remember { mutableIntStateOf(currentLauncherIconIndex(context)) }
    var pendingIconIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.launcher_icon_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        launcherIconOptions.forEachIndexed { index, option ->
            val selected = index == selectedIconIndex
            val rowShape = RoundedCornerShape(24.dp)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(rowShape)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = rowShape,
                    )
                    .clickable {
                        if (!selected) pendingIconIndex = index
                    },
                shape = rowShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(
                        painter = painterResource(option.drawableRes),
                        contentDescription = stringResource(option.labelRes),
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium)
                        if (selected) {
                            Text(
                                stringResource(R.string.launcher_icon_current),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.launcher_icon_current),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.launcher_icon_settings_title)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )

    if (pendingIconIndex >= 0) {
        val target = launcherIconOptions[pendingIconIndex]
        AlertDialog(
            onDismissRequest = { pendingIconIndex = -1 },
            title = { Text(stringResource(R.string.launcher_icon_restart_title)) },
            text = {
                Text(
                    stringResource(R.string.launcher_icon_restart_message, stringResource(target.labelRes)),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newIndex = pendingIconIndex
                        pendingIconIndex = -1
                        try {
                            applyLauncherIcon(context, launcherIconOptions[newIndex])
                            selectedIconIndex = newIndex
                            context.startActivity(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            context.findActivity()?.finishAffinity()
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                R.string.launcher_icon_apply_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.launcher_icon_restart_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingIconIndex = -1 }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun currentLauncherIconIndex(context: Context): Int {
    val packageManager = context.packageManager
    launcherIconOptions.forEachIndexed { index, option ->
        val state = packageManager.getComponentEnabledSetting(ComponentName(context, option.componentClassName))
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return index
    }
    return launcherIconOptions.indexOfFirst { it.componentClassName == DEFAULT_LAUNCHER_ALIAS }.coerceAtLeast(0)
}

private fun applyLauncherIcon(context: Context, selected: LauncherIconOption) {
    val packageManager = context.packageManager
    val flags = PackageManager.DONT_KILL_APP
    packageManager.setComponentEnabledSetting(
        ComponentName(context, selected.componentClassName),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        flags,
    )
    launcherIconOptions.forEach { option ->
        if (option.componentClassName != selected.componentClassName) {
            packageManager.setComponentEnabledSetting(
                ComponentName(context, option.componentClassName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flags,
            )
        }
    }
    packageManager.setComponentEnabledSetting(
        ComponentName(context, "com.metrolist.music.MainActivityStatic"),
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        flags,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
