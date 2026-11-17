package com.metrolist.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.zuno.ZunoAnnouncement
import com.metrolist.music.zuno.ZunoPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZunoNotificationsScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var notifications by remember { mutableStateOf<List<ZunoAnnouncement>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        notifications = withContext(Dispatchers.IO) {
            ZunoPanel.fetchAnnouncements(context)
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (loaded && notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No new notifications", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Admin announcements and offers will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(notifications, key = { it.id }) { notification ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!notification.linkUrl.isNullOrBlank() && notification.linkUrl.startsWith("https://")) {
                                    Modifier.clickable { runCatching { uriHandler.openUri(notification.linkUrl) } }
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(25.dp),
                                )
                            }
                            Spacer(Modifier.size(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        notification.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!notification.linkUrl.isNullOrBlank() && notification.linkUrl.startsWith("https://")) {
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_forward),
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                if (notification.body.isNotBlank()) {
                                    Text(
                                        notification.body,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
