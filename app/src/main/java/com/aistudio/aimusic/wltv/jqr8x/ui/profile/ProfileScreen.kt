package com.aistudio.aimusic.wltv.jqr8x.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.player.LocalMusicPlayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    repositoryOverride: MusicRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val playerManager = LocalMusicPlayer.current

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var mySongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(repository) {
        launch {
            repository.getUserProfile().collectLatest {
                profile = it
            }
        }
        launch {
            repository.getMySongs().collectLatest {
                mySongs = it
            }
        }
    }

    val pinnedSongs = remember(mySongs) { mySongs.filter { it.isPinned } }
    val favoriteCount = remember(mySongs) { mySongs.count { it.isFavorite } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!profile?.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile!!.photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile photo placeholder",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile?.displayName?.ifBlank { "User" } ?: "User",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = profile?.email ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(8.dp))
        AssistChip(
            onClick = onNavigateToEditProfile,
            leadingIcon = {
                Icon(
                    if (profile?.isPublicProfile != false) Icons.Default.Public else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(if (profile?.isPublicProfile != false) "Public Creator Profile" else "Private Profile")
            }
        )

        if (profile?.bio?.isNotBlank() == true) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = profile!!.bio,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        // Profile Completion Prompt
        if (profile?.displayName.isNullOrBlank() || profile?.bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Complete your profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Add your name and let people know who's making the music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Add Profile Info")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Creator Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileStatCard(label = "Songs", value = mySongs.size.toString(), modifier = Modifier.weight(1f))
            ProfileStatCard(label = "Pinned", value = pinnedSongs.size.toString(), modifier = Modifier.weight(1f))
            ProfileStatCard(label = "Favorites", value = favoriteCount.toString(), modifier = Modifier.weight(1f))
        }

        if (pinnedSongs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Pinned Songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pinnedSongs.forEach { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerManager?.playSong(song.audioUrl, song.title) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold)
                                Text(song.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play pinned song", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Credits Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Token, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Available Credits",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${profile?.credits ?: 0} Credits",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Actions
        ProfileMenuItem(
            icon = Icons.Default.Edit,
            label = "Edit Profile",
            onClick = onNavigateToEditProfile
        )
        ProfileMenuItem(
            icon = Icons.Default.Settings,
            label = "Account Settings",
            onClick = onNavigateToSettings
        )
        ProfileMenuItem(
            icon = Icons.Default.Language,
            label = "Language (${profile?.language ?: "English"})",
            onClick = onNavigateToSettings
        )
        ProfileMenuItem(
            icon = Icons.Default.Help,
            label = "Support & Help",
            onClick = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Word & Light TV • Christians Strong Foundation Ministry",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Psalm 119:105",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
