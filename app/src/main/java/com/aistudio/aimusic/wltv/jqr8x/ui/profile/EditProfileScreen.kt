package com.aistudio.aimusic.wltv.jqr8x.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    repositoryOverride: MusicRepository? = null
) {
    val context = LocalContext.current
    val repository = remember(repositoryOverride) { repositoryOverride ?: MusicRepository(context) }
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var isPublicProfile by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var hasInitialized by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUrl = uri.toString()
        }
    }

    LaunchedEffect(repository) {
        repository.getUserProfile().collectLatest { profile ->
            if (!hasInitialized && profile != null) {
                displayName = profile.displayName
                bio = profile.bio
                photoUrl = profile.photoUrl
                isPublicProfile = profile.isPublicProfile
                hasInitialized = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                saveError = null
                                runCatching {
                                    repository.updateUserProfile(displayName, bio, photoUrl, isPublicProfile)
                                }.onSuccess {
                                    isSaving = false
                                    onNavigateBack()
                                }.onFailure {
                                    isSaving = false
                                    saveError = it.localizedMessage ?: "Failed to save profile"
                                }
                            }
                        },
                        enabled = !isSaving && displayName.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Default avatar",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Surface(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Select profile photo", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Short Biography") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Let people know who's making the music...") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Profile information helps other users find your music when your profile is set to public.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth()
            )

            if (saveError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = saveError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Public Profile", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Allow others to see your profile and pinned songs.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isPublicProfile, onCheckedChange = { isPublicProfile = it })
                }
            }
        }
    }
}
