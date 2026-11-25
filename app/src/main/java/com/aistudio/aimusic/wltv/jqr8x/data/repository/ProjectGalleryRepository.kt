package com.aistudio.aimusic.wltv.jqr8x.data.repository

import android.content.Context
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectStatus
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeProjectType
import com.aistudio.aimusic.wltv.jqr8x.data.model.ProjectCoverTheme
import com.aistudio.aimusic.wltv.jqr8x.util.OperationType
import com.aistudio.aimusic.wltv.jqr8x.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CreativeProjectInput(
    val title: String,
    val genre: String,
    val projectType: CreativeProjectType = CreativeProjectType.SONG,
    val language: String = "English",
    val description: String = "",
    val status: CreativeProjectStatus = CreativeProjectStatus.IN_PROGRESS,
    val favorite: Boolean = false,
    val coverTheme: ProjectCoverTheme = ProjectCoverTheme.PURPLE_GOLD,
    val linkedSourceCollection: String = "",
    val linkedSourceId: String = ""
)

interface ProjectGalleryDataSource {
    fun observeProjects(userId: String): Flow<List<CreativeProject>>
    suspend fun createProject(input: CreativeProjectInput): Result<CreativeProject>
    suspend fun updateProject(projectId: String, input: CreativeProjectInput): Result<Unit>
    suspend fun toggleFavorite(projectId: String, favorite: Boolean): Result<Unit>
    suspend fun deleteProject(projectId: String): Result<Unit>
}

class ProjectGalleryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ProjectGalleryDataSource {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val COLLECTION_CREATIVE_PROJECTS = "creative_projects"
        private val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        val DEFAULT_GENRES = listOf(
            "Gospel",
            "Worship",
            "Praise",
            "Highlife",
            "Choir",
            "Afrobeat",
            "Reggae",
            "Instrumental"
        )

        val DEFAULT_LANGUAGES = listOf(
            "English",
            "Twi",
            "Mixed English + Twi",
            "Akan",
            "Ga",
            "Ewe"
        )

        fun isValidDocumentId(id: String): Boolean =
            id.length in 1..128 && VALID_DOC_ID_REGEX.matches(id)
    }

    override fun observeProjects(userId: String): Flow<List<CreativeProject>> = flow {
        val cleanUid = userId.trim()
        if (cleanUid.isEmpty() || auth.currentUser?.uid != cleanUid) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection(COLLECTION_CREATIVE_PROJECTS)
                .whereEqualTo("userId", cleanUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            CreativeProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedByDescending { project ->
                        (project.createdAt ?: project.updatedAt)?.toDate()?.time ?: Long.MAX_VALUE
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == cleanUid) {
                        handleFirestoreError(
                            exceptionToLog,
                            OperationType.LIST,
                            COLLECTION_CREATIVE_PROJECTS
                        )
                    }
                    throw firestoreError ?: error
                }
        )
    }

    override suspend fun createProject(input: CreativeProjectInput): Result<CreativeProject> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Please sign in to save creative projects."))

        val cleanTitle = input.title.trim().take(120)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Project title cannot be empty."))
        }
        val cleanGenre = input.genre.trim().ifBlank { "Gospel" }.take(60)
        val cleanLanguage = input.language.trim().ifBlank { "English" }.take(50)
        val cleanDescription = input.description.trim().take(1000)
        val cleanSourceCollection = input.linkedSourceCollection.trim().take(60)
        val cleanSourceId = input.linkedSourceId.trim().take(128)

        val docRef = db.collection(COLLECTION_CREATIVE_PROJECTS).document()
        val payload = mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "genre" to cleanGenre,
            "projectType" to input.projectType.name,
            "language" to cleanLanguage,
            "description" to cleanDescription,
            "status" to input.status.name,
            "favorite" to input.favorite,
            "coverTheme" to input.coverTheme.name,
            "linkedSourceCollection" to cleanSourceCollection,
            "linkedSourceId" to cleanSourceId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(
                CreativeProject(
                    id = docRef.id,
                    userId = uid,
                    title = cleanTitle,
                    genre = cleanGenre,
                    projectType = input.projectType.name,
                    language = cleanLanguage,
                    description = cleanDescription,
                    status = input.status.name,
                    favorite = input.favorite,
                    coverTheme = input.coverTheme.name,
                    linkedSourceCollection = cleanSourceCollection,
                    linkedSourceId = cleanSourceId
                )
            )
        } catch (e: CancellationException) {
            val firestoreError = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null) throw e
            handleFirestoreError(firestoreError, OperationType.CREATE, docRef.path)
            Result.failure(firestoreError)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun updateProject(
        projectId: String,
        input: CreativeProjectInput
    ): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Please sign in to update projects."))
        if (!isValidDocumentId(projectId)) {
            return Result.failure(IllegalArgumentException("Invalid project identifier."))
        }
        val cleanTitle = input.title.trim().take(120)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Project title cannot be empty."))
        }
        val cleanGenre = input.genre.trim().ifBlank { "Gospel" }.take(60)
        val cleanLanguage = input.language.trim().ifBlank { "English" }.take(50)
        val cleanDescription = input.description.trim().take(1000)

        val docRef = db.collection(COLLECTION_CREATIVE_PROJECTS).document(projectId)
        val payload = mapOf(
            "title" to cleanTitle,
            "genre" to cleanGenre,
            "projectType" to input.projectType.name,
            "language" to cleanLanguage,
            "description" to cleanDescription,
            "status" to input.status.name,
            "favorite" to input.favorite,
            "coverTheme" to input.coverTheme.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            val firestoreError = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null) throw e
            handleFirestoreError(firestoreError, OperationType.UPDATE, docRef.path)
            Result.failure(firestoreError)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun toggleFavorite(projectId: String, favorite: Boolean): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Please sign in to update favorites."))
        if (!isValidDocumentId(projectId)) {
            return Result.failure(IllegalArgumentException("Invalid project identifier."))
        }
        val docRef = db.collection(COLLECTION_CREATIVE_PROJECTS).document(projectId)
        return try {
            docRef.update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            val firestoreError = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null) throw e
            handleFirestoreError(firestoreError, OperationType.UPDATE, docRef.path)
            Result.failure(firestoreError)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Please sign in to delete projects."))
        if (!isValidDocumentId(projectId)) {
            return Result.failure(IllegalArgumentException("Invalid project identifier."))
        }
        val docRef = db.collection(COLLECTION_CREATIVE_PROJECTS).document(projectId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            val firestoreError = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null) throw e
            handleFirestoreError(firestoreError, OperationType.DELETE, docRef.path)
            Result.failure(firestoreError)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
