package com.aistudio.aimusic.wltv.jqr8x.ui.gallery

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectStatus
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.ProjectCoverTheme
import com.aistudio.aimusic.wltv.jqr8x.data.repository.CreativeProjectInput
import com.aistudio.aimusic.wltv.jqr8x.data.repository.MusicRepository
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryDataSource
import com.aistudio.aimusic.wltv.jqr8x.data.repository.ProjectGalleryRepository
import com.aistudio.aimusic.wltv.jqr8x.ui.library.LibraryScreen
import com.aistudio.aimusic.wltv.jqr8x.ui.navigation.AppNavigation
import com.aistudio.aimusic.wltv.jqr8x.ui.player.MusicPlayerManager
import com.aistudio.aimusic.wltv.jqr8x.ui.theme.WordAndLightTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

private class FakeProjectGalleryDataSource(
    initialProjects: List<CreativeProject> = emptyList(),
    var failObserve: Boolean = false
) : ProjectGalleryDataSource {

    private val projectsFlow = MutableStateFlow(initialProjects)
    private var nextId = initialProjects.size + 1

    override fun observeProjects(userId: String): Flow<List<CreativeProject>> = flow {
        if (failObserve) {
            throw FirebaseFirestoreException(
                "PERMISSION_DENIED: Missing or insufficient permissions.",
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }
        projectsFlow.map { list ->
            list.filter { it.userId == userId }
        }.collect { emit(it) }
    }

    override suspend fun createProject(input: CreativeProjectInput): Result<CreativeProject> {
        val cleanTitle = input.title.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Project title cannot be empty."))
        }
        val now = Timestamp(Date(1_795_000_000_000L + nextId * 86_400_000L))
        val created = CreativeProject(
            id = "proj_${nextId++}",
            userId = "user_test_1",
            title = cleanTitle,
            genre = input.genre.trim().ifBlank { "Gospel" },
            projectType = input.projectType.name,
            language = input.language.trim().ifBlank { "English" },
            description = input.description.trim(),
            status = input.status.name,
            favorite = input.favorite,
            coverTheme = input.coverTheme.name,
            linkedSourceCollection = input.linkedSourceCollection,
            linkedSourceId = input.linkedSourceId,
            createdAt = now,
            updatedAt = now
        )
        projectsFlow.value = listOf(created) + projectsFlow.value
        return Result.success(created)
    }

    override suspend fun updateProject(projectId: String, input: CreativeProjectInput): Result<Unit> {
        projectsFlow.value = projectsFlow.value.map { existing ->
            if (existing.id == projectId) {
                existing.copy(
                    title = input.title.trim(),
                    genre = input.genre.trim(),
                    projectType = input.projectType.name,
                    language = input.language.trim(),
                    description = input.description.trim(),
                    status = input.status.name,
                    favorite = input.favorite,
                    coverTheme = input.coverTheme.name
                )
            } else {
                existing
            }
        }
        return Result.success(Unit)
    }

    override suspend fun toggleFavorite(projectId: String, favorite: Boolean): Result<Unit> {
        projectsFlow.value = projectsFlow.value.map { existing ->
            if (existing.id == projectId) existing.copy(favorite = favorite) else existing
        }
        return Result.success(Unit)
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        projectsFlow.value = projectsFlow.value.filterNot { it.id == projectId }
        return Result.success(Unit)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectGalleryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var testMusicRepository: MusicRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val sampleOlderDate = Timestamp(Date(1_760_000_000_000L))
    private val sampleNewerDate = Timestamp(Date(1_790_000_000_000L))

    private val seedProjects = listOf(
        CreativeProject(
            id = "p_gospel_1",
            userId = "user_test_1",
            title = "Word & Light Dawn Anthem",
            genre = "Gospel",
            projectType = CreativeProjectType.SONG.name,
            language = "Mixed English + Twi",
            description = "Opening sanctuary choir praise for Word & Light TV",
            status = CreativeProjectStatus.COMPLETED.name,
            favorite = true,
            coverTheme = ProjectCoverTheme.PURPLE_GOLD.name,
            createdAt = sampleNewerDate,
            updatedAt = sampleNewerDate
        ),
        CreativeProject(
            id = "p_highlife_2",
            userId = "user_test_1",
            title = "Aseda Highlife Praise",
            genre = "Highlife",
            projectType = CreativeProjectType.AUDIO_MIX.name,
            language = "Twi",
            description = "Mastered 320kbps MP3 live guitar stems",
            status = CreativeProjectStatus.IN_PROGRESS.name,
            favorite = false,
            coverTheme = ProjectCoverTheme.GOLD_RADIANCE.name,
            createdAt = sampleOlderDate,
            updatedAt = sampleOlderDate
        )
    )

    @Before
    fun setUpFirebase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "gallery-test-app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.aimusic.wltv.jqr8x")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build(),
                "gallery-test-app"
            ).also { initialized ->
                val db = FirebaseFirestore.getInstance(
                    initialized,
                    "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
                )
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
        firestore = FirebaseFirestore.getInstance(
            app,
            "ai-studio-android-wordligh-6c38febb-2572-4fb1-840d-e10fc09376b8"
        )
        auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        testMusicRepository = MusicRepository(firestore, auth)
    }

    @Test
    fun galleryScreen_displaysSavedProjectsWithTitleGenreAndCreationDate_andFiltersByGenre() {
        val fakeSource = FakeProjectGalleryDataSource(seedProjects)
        val viewModel = ProjectGalleryViewModel(fakeSource, "user_test_1")

        composeRule.setContent {
            WordAndLightTheme {
                ProjectGalleryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    musicRepositoryOverride = testMusicRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("project_gallery_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_projects_grid")
            .performScrollToNode(hasTestTag("gallery_project_card_p_gospel_1"))
        composeRule.onNodeWithText("Word & Light Dawn Anthem").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_projects_grid")
            .performScrollToNode(hasText("Created ${formatCreationDate(sampleNewerDate)}"))
        composeRule.onNodeWithText(
            "Created ${formatCreationDate(sampleNewerDate)}"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_projects_grid")
            .performScrollToNode(hasTestTag("gallery_project_card_p_highlife_2"))
        composeRule.onNodeWithText("Aseda Highlife Praise").assertIsDisplayed()

        // Filter by Highlife genre chip
        composeRule.onNodeWithTag("gallery_genre_chip_highlife").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Aseda Highlife Praise").assertIsDisplayed()
        composeRule.onNodeWithText("Word & Light Dawn Anthem").assertDoesNotExist()

        // Reset via All chip
        composeRule.onNodeWithTag("gallery_genre_chip_all").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_projects_grid")
            .performScrollToNode(hasTestTag("gallery_project_card_p_gospel_1"))
        composeRule.onNodeWithText("Word & Light Dawn Anthem").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_createEditAndDeleteProject_updatesDisplayedMetadata() {
        val fakeSource = FakeProjectGalleryDataSource(emptyList())
        val viewModel = ProjectGalleryViewModel(fakeSource, "user_test_1")

        composeRule.setContent {
            WordAndLightTheme {
                ProjectGalleryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    musicRepositoryOverride = testMusicRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gallery_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_empty_add_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Blank title validation keeps editor open
        composeRule.onNodeWithTag("gallery_editor_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Please enter a project title").assertIsDisplayed()

        // Fill valid metadata and save
        composeRule.onNodeWithTag("gallery_editor_title_input").performTextInput("Psalm 119 Light")
        composeRule.onNodeWithTag("gallery_editor_genre_chip_worship").performScrollTo().performClick()
        composeRule.onNodeWithTag("gallery_editor_type_lyrics").performScrollTo().performClick()
        composeRule.onNodeWithTag("gallery_editor_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Psalm 119 Light").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_genre_chip_worship").assertIsDisplayed()
        composeRule.onAllNodesWithText("Worship").assertCountEquals(2)

        // Open detail dialog -> Edit title -> Save -> Verify updated metadata
        composeRule.onNodeWithText("Psalm 119 Light").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_detail_edit_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_editor_title_input").performTextReplacement("Psalm 119 Morning Light")
        composeRule.onNodeWithTag("gallery_editor_save_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Psalm 119 Morning Light").assertIsDisplayed()

        // Open detail dialog -> delete with confirmation
        composeRule.onNodeWithText("Psalm 119 Morning Light").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_detail_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_confirm_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gallery_empty_state").assertIsDisplayed()
    }

    @Test
    fun galleryScreen_errorStateAndRetryRecoverCleanly() {
        val fakeSource = FakeProjectGalleryDataSource(seedProjects, failObserve = true)
        val viewModel = ProjectGalleryViewModel(fakeSource, "user_test_1")

        composeRule.setContent {
            WordAndLightTheme {
                ProjectGalleryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    musicRepositoryOverride = testMusicRepository
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gallery_error_state").assertIsDisplayed()

        fakeSource.failObserve = false
        composeRule.onNodeWithTag("gallery_retry_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Word & Light Dawn Anthem").assertIsDisplayed()
    }

    @Test
    fun libraryScreenAndAppNavigation_openProjectGalleryCleanly() {
        var libraryGalleryClicked = false
        composeRule.setContent {
            WordAndLightTheme {
                LibraryScreen(
                    onNavigateToFavorites = {},
                    repositoryOverride = testMusicRepository,
                    onNavigateToProjectGallery = { libraryGalleryClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_open_gallery_button").assertIsDisplayed().performClick()
        assertTrue(libraryGalleryClicked)
    }

    @Test
    fun appNavigation_fromProjectsTab_opensProjectGalleryAndBacksOut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPlayerManager = MusicPlayerManager(context = context, connectController = false)
        val fakeSource = FakeProjectGalleryDataSource(seedProjects)

        composeRule.setContent {
            WordAndLightTheme {
                AppNavigation(
                    uid = "user_test_1",
                    repositoryOverride = testMusicRepository,
                    playerManagerOverride = testPlayerManager,
                    projectGalleryRepositoryOverride = fakeSource
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_open_gallery_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("project_gallery_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Word & Light Dawn Anthem").assertIsDisplayed()

        composeRule.onNodeWithTag("gallery_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_open_gallery_button").assertIsDisplayed()
    }

    @Test
    fun projectGalleryRepository_validatesAuthAndDocumentIdBoundaries() = runBlocking {
        val repo = ProjectGalleryRepository(firestore, auth)

        val unauthCreate = repo.createProject(
            CreativeProjectInput(title = "Test", genre = "Gospel")
        )
        assertTrue(unauthCreate.isFailure)

        val invalidUpdate = repo.updateProject(
            projectId = "bad/id",
            input = CreativeProjectInput(title = "Test", genre = "Gospel")
        )
        assertTrue(invalidUpdate.isFailure)

        assertEquals(false, ProjectGalleryRepository.isValidDocumentId("bad/id"))
        assertEquals(true, ProjectGalleryRepository.isValidDocumentId("valid_project_123"))
    }
}
