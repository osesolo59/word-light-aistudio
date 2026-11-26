package com.aistudio.aimusic.wltv.jqr8x.data.repository

import android.content.Context
import com.aistudio.aimusic.wltv.jqr8x.R
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiIdeasResult
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsRequest
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiLyricsResult
import com.aistudio.aimusic.wltv.jqr8x.data.ai.AiTextGenerator
import com.aistudio.aimusic.wltv.jqr8x.data.ai.FirebaseAiTextGenerator
import com.aistudio.aimusic.wltv.jqr8x.data.ai.LyricsStreamEvent
import com.aistudio.aimusic.wltv.jqr8x.data.demo.IdeaGeneratorEngine
import com.aistudio.aimusic.wltv.jqr8x.data.bilingual.BilingualGospelHelper
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMixTrack
import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea
import com.aistudio.aimusic.wltv.jqr8x.data.model.Lyric
import com.aistudio.aimusic.wltv.jqr8x.data.model.MusicVideoProject
import com.aistudio.aimusic.wltv.jqr8x.data.model.Playlist
import com.aistudio.aimusic.wltv.jqr8x.data.model.Song
import com.aistudio.aimusic.wltv.jqr8x.data.model.TimedBilingualCue
import com.aistudio.aimusic.wltv.jqr8x.data.model.UserProfile
import com.aistudio.aimusic.wltv.jqr8x.data.model.VideoProject
import com.aistudio.aimusic.wltv.jqr8x.util.OperationType
import com.aistudio.aimusic.wltv.jqr8x.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
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

class MusicRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val aiTextGenerator: AiTextGenerator = FirebaseAiTextGenerator()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val IS_DEMO_MODE = true // Strict Free Demo Mode remains active for Music/Video and fallback
    }

    private fun requireUserId(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in")

    private fun <T> Flow<T>.handleSnapshotErrors(
        operationType: OperationType,
        path: String,
        uid: String?,
        fallback: T
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (uid == null || auth.currentUser?.uid == uid)) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        emit(fallback)
    }

    suspend fun ensureUserProfileExists() {
        val user = auth.currentUser ?: return
        val userRef = db.collection("users").document(user.uid)
        try {
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val initialProfile = mapOf(
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: ""),
                    "bio" to "",
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "credits" to 5,
                    "appearance" to "System Default",
                    "language" to "English",
                    "isPublicProfile" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                userRef.set(initialProfile).await()
                syncPublicProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: "",
                    bio = "",
                    photoUrl = user.photoUrl?.toString() ?: "",
                    isPublicProfile = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    private suspend fun syncPublicProfile(
        uid: String,
        displayName: String,
        bio: String,
        photoUrl: String,
        isPublicProfile: Boolean
    ) {
        val publicRef = db.collection("public_profiles").document(uid)
        val payload = mapOf(
            "displayName" to displayName,
            "bio" to bio,
            "photoUrl" to photoUrl,
            "isPublicProfile" to isPublicProfile,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            publicRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, publicRef.path)
        }
    }

    suspend fun generateMusicViaBackend(
        title: String,
        prompt: String,
        lyrics: String,
        genre: String,
        modelId: String,
        taskId: String,
        language: String = "English",
        mood: String = "",
        tempo: String = "",
        instruments: List<String> = emptyList(),
        vocalStyle: String = ""
    ): Song {
        val cleanTitle = title.trim().take(100)
        val cleanGenre = genre.trim().take(50)
        val cleanLanguage = language.trim().ifBlank { "English" }.take(50)
        val cleanMood = mood.trim().take(100)
        val cleanTempo = tempo.trim().take(50)
        val cleanInstruments = instruments.map { it.trim() }.filter { it.isNotEmpty() }.take(20)
        val cleanVocalStyle = vocalStyle.trim().take(100)
        val cleanPrompt = prompt.trim().ifBlank {
            listOfNotNull(
                cleanGenre.takeIf { it.isNotBlank() },
                cleanLanguage.takeIf { it.isNotBlank() },
                cleanMood.takeIf { it.isNotBlank() },
                cleanTempo.takeIf { it.isNotBlank() },
                cleanVocalStyle.takeIf { it.isNotBlank() },
                cleanInstruments.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ).joinToString(" • ")
        }.take(1000)

        // Strict Free Demo Mode: always store demo song in Firestore with sample audio; never call paid backend functions
        kotlinx.coroutines.delay(400)
        val uid = requireUserId()
        ensureUserProfileExists()
        val profile = try {
            db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }

        val audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
        val creatorName = profile?.displayName?.ifBlank { "User" } ?: "User"
        val docRef = db.collection("songs").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "prompt" to cleanPrompt,
            "lyrics" to lyrics.trim().take(10000),
            "genre" to cleanGenre,
            "language" to cleanLanguage,
            "mood" to cleanMood,
            "tempo" to cleanTempo,
            "instruments" to cleanInstruments,
            "vocalStyle" to cleanVocalStyle,
            "audioUrl" to audioUrl,
            "isPublic" to false,
            "isDemo" to true,
            "isFavorite" to false,
            "isPinned" to false,
            "allowRemix" to true,
            "likeStatus" to 0,
            "creatorName" to creatorName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        return Song(
            id = docRef.id,
            userId = uid,
            title = cleanTitle,
            prompt = cleanPrompt,
            lyrics = lyrics.trim().take(10000),
            genre = cleanGenre,
            language = cleanLanguage,
            mood = cleanMood,
            tempo = cleanTempo,
            instruments = cleanInstruments,
            vocalStyle = cleanVocalStyle,
            audioUrl = audioUrl,
            isPublic = false,
            isDemo = true,
            creatorName = creatorName
        )
    }

    fun activeAiModelName(): String = aiTextGenerator.modelName

    suspend fun generateAiLyrics(
        request: AiLyricsRequest
    ): AiLyricsResult = aiTextGenerator.generateLyrics(request)

    fun generateAiLyricsStream(
        request: AiLyricsRequest
    ): Flow<LyricsStreamEvent> = aiTextGenerator.generateLyricsStream(request)

    fun buildDemoLyricsResult(request: AiLyricsRequest): AiLyricsResult {
        val resolvedTitle = request.title.trim()
            .ifBlank { request.topic.trim() }
            .ifBlank { "Light of Praise" }
            .take(100)
        val cleanLanguage = request.language.trim().ifBlank { "English" }
        val cleanTopic = request.topic.trim().ifBlank { resolvedTitle }
        return if (cleanLanguage.lowercase() in setOf("ga", "ewe") && request.sectionLanguages.isEmpty()) {
            val demoBody = buildDemoLyricsText(
                title = resolvedTitle,
                topic = cleanTopic,
                genre = request.genre,
                mood = request.mood,
                language = cleanLanguage
            )
            AiLyricsResult(
                title = resolvedTitle,
                lyrics = demoBody,
                scriptureReference = request.scriptureReference.trim(),
                modelName = "demo-local-template",
                isDemo = true,
                captionCues = BilingualGospelHelper.generateCuesFromLyrics(demoBody)
            )
        } else {
            val (demoBody, cues) = BilingualGospelHelper.buildNaturalBilingualLyricsAndCues(
                title = resolvedTitle,
                topic = cleanTopic,
                genre = request.genre,
                mood = request.mood,
                primaryLanguage = cleanLanguage,
                sectionLanguages = request.sectionLanguages.ifEmpty {
                    BilingualGospelHelper.defaultSectionLanguages(cleanLanguage)
                }
            )
            AiLyricsResult(
                title = resolvedTitle,
                lyrics = demoBody,
                scriptureReference = request.scriptureReference.trim(),
                modelName = "demo-local-template",
                isDemo = true,
                captionCues = cues
            )
        }
    }

    suspend fun generateLyricsViaBackend(
        topic: String,
        genre: String,
        mood: String,
        language: String = "English"
    ): String {
        val cleanTopic = topic.trim()
        return buildDemoLyricsText(
            title = cleanTopic.ifBlank { "Word & Light Praise" },
            topic = cleanTopic,
            genre = genre,
            mood = mood,
            language = language
        )
    }

    private fun buildDemoLyricsText(
        title: String,
        topic: String,
        genre: String,
        mood: String,
        language: String
    ): String {
        val cleanTopic = topic.trim().ifBlank { title.trim() }
        val cleanGenre = genre.trim().ifBlank { "Worship" }
        val cleanMood = mood.trim().ifBlank { "Uplifting" }
        val cleanLanguage = language.trim().ifBlank { "English" }
        return when (cleanLanguage.lowercase()) {
            "twi", "akan", "mixed english + twi" -> {
                BilingualGospelHelper.buildNaturalBilingualLyricsAndCues(
                    title = title,
                    topic = cleanTopic,
                    genre = cleanGenre,
                    mood = cleanMood,
                    primaryLanguage = cleanLanguage
                ).first
            }
            "ga" -> """
                [Demo Lyrics — $cleanGenre | $cleanLanguage | $cleanMood]
                Title: $title

                [Intro]
                ($cleanMood $cleanGenre rhythm) Nyɔŋmɔ ji wɔ la kɛ wɔ wala

                [Verse 1]
                Nyɔŋmɔ ji wɔ la kɛ wɔ wala ($cleanTopic)
                Wɔjieɔ Oyi yɛ miishɛɛ mli
                Kɛ $cleanMood tsui wɔlaa lala
                Osuɔmɔ hiɔ shi daa

                [Verse 2]
                Leebi nɛɛ wɔdaa Bo shi, Nuŋtsɔ
                Oduromɔ haa wɔ hewalɛ daa
                Wɔnyiɛɔ Ola mli yɛ toijɔlɛ mli
                Yesu ji wɔ yiwalaherelɔ

                [Chorus]
                Yijiemɔ aha Bo, Nuŋtsɔ
                Word & Light damɔ wɔmasɛi
                Wɔkɛ $cleanGenre lala woɔ Ohiɛ nyam
                Haleluya, daa kɛ daa

                [Bridge]
                Wɔwóɔ wɔnine nɔ wɔhaa Ogbɛi krɔŋkrɔŋ
                Shidaa fɛɛ ji Onɔ

                [Outro]
                Amen, Amen — Haleluya ($cleanLanguage Praise)

                (Demo Mode: AI lyric generation is simulated locally using a template)
            """.trimIndent()
            "ewe" -> """
                [Demo Lyrics — $cleanGenre | $cleanLanguage | $cleanMood]
                Title: $title

                [Intro]
                ($cleanMood $cleanGenre beat) Mawu nye míaƒe kekeli kple agbe

                [Verse 1]
                Mawu nye míaƒe kekeli kple agbe ($cleanTopic)
                Míekafua Wò ŋkɔ le dzidzɔ me
                Le $cleanMood dzi me míedzia ha
                Wò lɔlɔ̃ nɔa anyi ɖaa

                [Verse 2]
                Ŋdi sia míedaa akpe na Wò, Aƒetɔ
                Wò amenuveve doa ŋusẽ mí
                Míezɔa Wò kekeli me le ŋutifafa me
                Yesu nye míaƒe xɔnametɔ

                [Chorus]
                Kafukafu na Wò, Aƒetɔ
                Word & Light le mía gbɔ
                Míetsɔ $cleanGenre ha doa Wò ɖe dzi
                Haleluya, tso mavɔ me yi mavɔ me

                [Bridge]
                Míekɔa míaƒe asiwo dzi na Wò ŋkɔ kɔkɔe
                Akpedada katã nye Tɔwò

                [Outro]
                Amen, Amen — Haleluya ($cleanLanguage Praise)

                (Demo Mode: AI lyric generation is simulated locally using a template)
            """.trimIndent()
            else -> """
                [Demo Lyrics — $cleanGenre | $cleanLanguage | $cleanMood]
                Title: $title

                [Intro]
                ($cleanMood $cleanGenre instrumental rise)

                [Verse 1]
                Walking in the promise of $cleanTopic
                Every note rises in a $cleanMood spirit
                Through $cleanGenre rhythms we lift our voice
                Your Word and Light make our hearts rejoice

                [Verse 2]
                Morning by morning Your mercies are new
                Every step anchored and steady in You
                Singing together with grateful delight
                Guided forever by heavenly light

                [Chorus]
                Shine Your light across the night
                Word and Light, our strength and song
                In $cleanLanguage praise we stand as one
                Forever singing of Your grace

                [Bridge]
                We lift our hands, we lift Your name high
                Hope overflows from earth to the sky

                [Outro]
                Hallelujah, Amen — Forever Your praise

                (Demo Mode: AI lyric generation is simulated locally using a template)
            """.trimIndent()
        }
    }

    suspend fun saveLyrics(
        title: String,
        lyrics: String,
        topic: String,
        genre: String,
        mood: String,
        language: String,
        scriptureReference: String = "",
        userInstructions: String = "",
        generationSource: String = "AI_GEMINI",
        sectionLanguages: Map<String, String> = emptyMap(),
        captionCues: List<TimedBilingualCue> = emptyList()
    ): String {
        val uid = requireUserId()
        ensureUserProfileExists()
        val docRef = db.collection("lyrics").document()
        val safeSource = if (generationSource == "DEMO") "DEMO" else "AI_GEMINI"
        val cleanSections = sectionLanguages.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(12)
            .associate { it.key.trim().take(40) to it.value.trim().take(30) }
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", title.trim().take(100))
            put("lyrics", lyrics.trim().take(10000))
            put("topic", topic.trim().take(200))
            put("genre", genre.trim().take(50))
            put("mood", mood.trim().take(100))
            put("language", language.trim().ifBlank { "English" }.take(50))
            if (scriptureReference.isNotBlank()) {
                put("scriptureReference", scriptureReference.trim().take(200))
            }
            if (userInstructions.isNotBlank()) {
                put("userInstructions", userInstructions.trim().take(500))
            }
            if (safeSource == "AI_GEMINI") {
                put("generationSource", safeSource)
            }
            if (cleanSections.isNotEmpty()) {
                put("sectionLanguages", cleanSections)
            }
            if (captionCues.isNotEmpty()) {
                put("captionCues", BilingualGospelHelper.cuesToFirestoreMaps(captionCues))
            }
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun getMyLyrics(): Flow<List<Lyric>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("lyrics")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Lyric::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "lyrics", uid, emptyList())
        )
    }

    suspend fun updateLyrics(
        lyricId: String,
        title: String,
        lyrics: String,
        topic: String,
        genre: String,
        mood: String,
        language: String,
        scriptureReference: String = "",
        userInstructions: String = "",
        generationSource: String = "DEMO",
        sectionLanguages: Map<String, String> = emptyMap(),
        captionCues: List<TimedBilingualCue> = emptyList()
    ) {
        requireUserId()
        val cleanTitle = title.trim().take(100)
        val cleanLyrics = lyrics.trim().take(10000)
        require(cleanTitle.isNotEmpty()) { "Lyrics title cannot be empty" }
        require(cleanLyrics.isNotEmpty()) { "Lyrics text cannot be empty" }
        val safeSource = if (generationSource == "AI_GEMINI") "AI_GEMINI" else "DEMO"
        val cleanSections = sectionLanguages.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(12)
            .associate { it.key.trim().take(40) to it.value.trim().take(30) }
        val payload = buildMap<String, Any> {
            put("title", cleanTitle)
            put("lyrics", cleanLyrics)
            put("topic", topic.trim().take(200))
            put("genre", genre.trim().take(50))
            put("mood", mood.trim().take(100))
            put("language", language.trim().ifBlank { "English" }.take(50))
            put("scriptureReference", scriptureReference.trim().take(200))
            put("userInstructions", userInstructions.trim().take(500))
            put("generationSource", safeSource)
            put("sectionLanguages", cleanSections)
            put("captionCues", BilingualGospelHelper.cuesToFirestoreMaps(captionCues))
        }
        val docRef = db.collection("lyrics").document(lyricId)
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteLyrics(lyricId: String) {
        val path = "lyrics/$lyricId"
        try {
            db.collection("lyrics").document(lyricId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun saveAudioMixProject(
        existingId: String = "",
        title: String,
        language: String,
        sectionLanguages: Map<String, String> = emptyMap(),
        lyrics: String = "",
        captionCues: List<TimedBilingualCue> = emptyList(),
        tracks: List<AudioMixTrack> = emptyList(),
        totalDurationMs: Long = 0L,
        localMp3Path: String = ""
    ): AudioMixProject {
        val uid = requireUserId()
        ensureUserProfileExists()
        val profile = try {
            db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }
        val creatorName = profile?.displayName?.ifBlank { "User" } ?: "User"
        val cleanTitle = title.trim().ifBlank { "English & Twi Mix" }.take(100)
        val cleanLanguage = language.trim().ifBlank { BilingualGospelHelper.LANGUAGE_MIXED_ENGLISH_TWI }.take(50)
        val cleanSections = sectionLanguages.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(12)
            .associate { it.key.trim().take(40) to it.value.trim().take(30) }
        val cleanLyrics = lyrics.trim().take(10000)
        val cleanMp3Path = localMp3Path.trim().take(1000)
        val safeDuration = totalDurationMs.coerceIn(0L, 3_600_000L)

        val col = db.collection("audio_mixes")
        val docRef = if (existingId.isNotBlank()) col.document(existingId) else col.document()
        val cueMaps = BilingualGospelHelper.cuesToFirestoreMaps(captionCues)
        val trackMaps = BilingualGospelHelper.tracksToFirestoreMaps(tracks)

        try {
            if (existingId.isNotBlank()) {
                val updatePayload = mapOf(
                    "title" to cleanTitle,
                    "language" to cleanLanguage,
                    "sectionLanguages" to cleanSections,
                    "lyrics" to cleanLyrics,
                    "captionCues" to cueMaps,
                    "tracks" to trackMaps,
                    "totalDurationMs" to safeDuration,
                    "localMp3Path" to cleanMp3Path
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "language" to cleanLanguage,
                    "sectionLanguages" to cleanSections,
                    "lyrics" to cleanLyrics,
                    "captionCues" to cueMaps,
                    "tracks" to trackMaps,
                    "totalDurationMs" to safeDuration,
                    "localMp3Path" to cleanMp3Path,
                    "isDemo" to true,
                    "creatorName" to creatorName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId.isNotBlank()) OperationType.UPDATE else OperationType.CREATE,
                docRef.path
            )
            throw e
        }

        return AudioMixProject(
            id = docRef.id,
            userId = uid,
            title = cleanTitle,
            language = cleanLanguage,
            sectionLanguages = cleanSections,
            lyrics = cleanLyrics,
            captionCues = captionCues,
            tracks = tracks,
            totalDurationMs = safeDuration,
            localMp3Path = cleanMp3Path,
            isDemo = true,
            creatorName = creatorName
        )
    }

    fun getMyAudioMixes(): Flow<List<AudioMixProject>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("audio_mixes")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AudioMixProject::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "audio_mixes", uid, emptyList())
        )
    }

    suspend fun deleteAudioMix(mixId: String) {
        val path = "audio_mixes/$mixId"
        try {
            db.collection("audio_mixes").document(mixId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    fun getMySongs(): Flow<List<Song>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("songs")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Song::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "songs", uid, emptyList())
        )
    }

    fun getTrendingSongs(): Flow<List<Song>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("songs")
                .whereEqualTo("isPublic", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Song::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        .take(10)
                }
                .handleSnapshotErrors(OperationType.LIST, "songs", uid, emptyList())
        )
    }

    fun getUserProfile(): Flow<UserProfile?> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid).snapshots()
                .map { it.toObject(UserProfile::class.java) }
                .handleSnapshotErrors(OperationType.GET, path, uid, null)
        )
    }

    suspend fun toggleFavorite(songId: String, isFavorite: Boolean) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId)
                .update("isFavorite", isFavorite)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    fun getFavoriteSongs(): Flow<List<Song>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("songs")
                .whereEqualTo("userId", uid)
                .whereEqualTo("isFavorite", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Song::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "songs", uid, emptyList())
        )
    }

    fun getGenerationHistory(): Flow<List<Map<String, Any>>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("tasks")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.map { doc -> doc.data ?: emptyMap() }
                }
                .handleSnapshotErrors(OperationType.LIST, "tasks", uid, emptyList())
        )
    }

    suspend fun generateVideoViaBackend(
        prompt: String,
        templateName: String,
        title: String = "",
        aspectRatio: String = "16:9",
        captions: String = "",
        transitionStyle: String = "Fade",
        durationPerImageSec: Int = 3,
        coverPhotoUri: String = "",
        photoUris: List<String> = emptyList(),
        localVideoPath: String = "",
        captionCues: List<TimedBilingualCue> = emptyList(),
        captionDisplayMode: String = "BOTH"
    ): VideoProject {
        if (localVideoPath.isBlank()) {
            kotlinx.coroutines.delay(1200)
        }
        val uid = requireUserId()
        ensureUserProfileExists()
        val profile = try {
            db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }

        val resolvedTitle = title.trim().ifBlank {
            if (prompt.isNotBlank()) prompt.trim().take(40) else "Video: $templateName"
        }.take(100)
        val cleanLocalPath = localVideoPath.trim().take(1000)
        val videoUrl = if (cleanLocalPath.isNotEmpty()) {
            if (cleanLocalPath.startsWith("file://") || cleanLocalPath.startsWith("content://")) {
                cleanLocalPath
            } else {
                "file://$cleanLocalPath"
            }
        } else {
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        }
        val safeRatio = if (aspectRatio in listOf("16:9", "9:16", "1:1")) aspectRatio else "16:9"
        val cleanPhotos = photoUris.map { it.trim().take(1000) }.filter { it.isNotEmpty() }.take(30)
        val cleanCover = coverPhotoUri.trim().take(1000)
        val cleanCaptions = captions.trim().take(1000)
        val cleanTransition = transitionStyle.trim().ifBlank { "Fade" }.take(50)
        val safeDuration = durationPerImageSec.coerceIn(1, 30)
        val safeCaptionMode = if (captionDisplayMode in listOf("BOTH", "ENGLISH_ONLY", "TWI_ONLY")) captionDisplayMode else "BOTH"

        val creatorName = profile?.displayName?.ifBlank { "User" } ?: "User"
        val docRef = db.collection("videos").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", resolvedTitle)
            put("prompt", prompt.trim().take(1000))
            put("templateName", templateName.trim().take(100))
            put("videoUrl", videoUrl)
            put("isDemo", true)
            put("creatorName", creatorName)
            put("createdAt", FieldValue.serverTimestamp())
            if (cleanPhotos.isNotEmpty() || cleanLocalPath.isNotEmpty() || cleanCaptions.isNotEmpty() || captionCues.isNotEmpty()) {
                put("aspectRatio", safeRatio)
                put("captions", cleanCaptions)
                put("transitionStyle", cleanTransition)
                put("durationPerImageSec", safeDuration)
                put("photoCount", cleanPhotos.size)
                put("coverPhotoUri", cleanCover)
                put("photoUris", cleanPhotos)
                put("localVideoPath", cleanLocalPath)
                if (captionCues.isNotEmpty()) {
                    put("captionCues", BilingualGospelHelper.cuesToFirestoreMaps(captionCues))
                    put("captionDisplayMode", safeCaptionMode)
                }
            }
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        return VideoProject(
            id = docRef.id,
            userId = uid,
            title = resolvedTitle,
            prompt = prompt.trim().take(1000),
            videoUrl = videoUrl,
            templateName = templateName.trim().take(100),
            aspectRatio = safeRatio,
            captions = cleanCaptions,
            transitionStyle = cleanTransition,
            durationPerImageSec = safeDuration,
            photoCount = cleanPhotos.size,
            coverPhotoUri = cleanCover,
            photoUris = cleanPhotos,
            localVideoPath = cleanLocalPath,
            captionCues = captionCues,
            captionDisplayMode = safeCaptionMode,
            isDemo = true,
            creatorName = creatorName
        )
    }

    suspend fun createMusicVideoViaBackend(
        songId: String,
        videoId: String,
        bibleVerse: String,
        title: String,
        aspectRatio: String = "16:9",
        captions: String = "",
        transitionStyle: String = "Fade",
        durationPerImageSec: Int = 3,
        coverPhotoUri: String = "",
        photoUris: List<String> = emptyList(),
        localVideoPath: String = "",
        captionCues: List<TimedBilingualCue> = emptyList(),
        captionDisplayMode: String = "BOTH"
    ): MusicVideoProject {
        if (localVideoPath.isBlank()) {
            kotlinx.coroutines.delay(1200)
        }
        val uid = requireUserId()
        ensureUserProfileExists()
        val profile = try {
            db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }

        val resolvedTitle = title.trim().ifEmpty { "Music Video ${System.currentTimeMillis()}" }.take(100)
        val cleanLocalPath = localVideoPath.trim().take(1000)
        val videoUrl = if (cleanLocalPath.isNotEmpty()) {
            if (cleanLocalPath.startsWith("file://") || cleanLocalPath.startsWith("content://")) {
                cleanLocalPath
            } else {
                "file://$cleanLocalPath"
            }
        } else {
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        }
        val safeRatio = if (aspectRatio in listOf("16:9", "9:16", "1:1")) aspectRatio else "16:9"
        val cleanPhotos = photoUris.map { it.trim().take(1000) }.filter { it.isNotEmpty() }.take(30)
        val cleanCover = coverPhotoUri.trim().take(1000)
        val cleanCaptions = captions.trim().take(1000)
        val cleanTransition = transitionStyle.trim().ifBlank { "Fade" }.take(50)
        val safeDuration = durationPerImageSec.coerceIn(1, 30)
        val safeCaptionMode = if (captionDisplayMode in listOf("BOTH", "ENGLISH_ONLY", "TWI_ONLY")) captionDisplayMode else "BOTH"

        val creatorName = profile?.displayName?.ifBlank { "User" } ?: "User"
        val docRef = db.collection("music_videos").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", resolvedTitle)
            put("songId", songId.trim().take(200))
            put("videoId", videoId.trim().take(200))
            put("bibleVerse", bibleVerse.trim().take(500))
            put("videoUrl", videoUrl)
            put("isDemo", true)
            put("creatorName", creatorName)
            put("createdAt", FieldValue.serverTimestamp())
            if (cleanPhotos.isNotEmpty() || cleanLocalPath.isNotEmpty() || cleanCaptions.isNotEmpty() || captionCues.isNotEmpty()) {
                put("aspectRatio", safeRatio)
                put("captions", cleanCaptions)
                put("transitionStyle", cleanTransition)
                put("durationPerImageSec", safeDuration)
                put("photoCount", cleanPhotos.size)
                put("coverPhotoUri", cleanCover)
                put("photoUris", cleanPhotos)
                put("localVideoPath", cleanLocalPath)
                if (captionCues.isNotEmpty()) {
                    put("captionCues", BilingualGospelHelper.cuesToFirestoreMaps(captionCues))
                    put("captionDisplayMode", safeCaptionMode)
                }
            }
        }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        return MusicVideoProject(
            id = docRef.id,
            userId = uid,
            title = resolvedTitle,
            songId = songId.trim().take(200),
            videoId = videoId.trim().take(200),
            bibleVerse = bibleVerse.trim().take(500),
            videoUrl = videoUrl,
            aspectRatio = safeRatio,
            captions = cleanCaptions,
            transitionStyle = cleanTransition,
            durationPerImageSec = safeDuration,
            photoCount = cleanPhotos.size,
            coverPhotoUri = cleanCover,
            photoUris = cleanPhotos,
            localVideoPath = cleanLocalPath,
            captionCues = captionCues,
            captionDisplayMode = safeCaptionMode,
            isDemo = true,
            creatorName = creatorName
        )
    }

    fun getMyVideos(): Flow<List<VideoProject>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("videos")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VideoProject::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "videos", uid, emptyList())
        )
    }

    suspend fun deleteVideo(videoId: String) {
        val path = "videos/$videoId"
        try {
            db.collection("videos").document(videoId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    fun getMyMusicVideos(): Flow<List<MusicVideoProject>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("music_videos")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MusicVideoProject::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "music_videos", uid, emptyList())
        )
    }

    suspend fun deleteMusicVideo(mvId: String) {
        val path = "music_videos/$mvId"
        try {
            db.collection("music_videos").document(mvId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        bio: String,
        photoUrl: String,
        isPublicProfile: Boolean
    ) {
        val uid = requireUserId()
        ensureUserProfileExists()
        val path = "users/$uid"
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to displayName.trim(),
                    "bio" to bio.trim(),
                    "photoUrl" to photoUrl,
                    "isPublicProfile" to isPublicProfile
                )
            ).await()
            syncPublicProfile(uid, displayName.trim(), bio.trim(), photoUrl, isPublicProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun updateAppearance(appearance: String) {
        val uid = requireUserId()
        ensureUserProfileExists()
        val path = "users/$uid"
        try {
            db.collection("users").document(uid).update("appearance", appearance).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun updateLanguage(language: String) {
        val uid = requireUserId()
        ensureUserProfileExists()
        val path = "users/$uid"
        try {
            db.collection("users").document(uid).update("language", language).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun deleteSong(songId: String) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    suspend fun updateSongDetails(songId: String, title: String, isPublic: Boolean) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId).update(
                mapOf(
                    "title" to title.trim(),
                    "isPublic" to isPublic
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun updateSongLikeStatus(songId: String, status: Int) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId).update("likeStatus", status).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun togglePinToProfile(songId: String, isPinned: Boolean) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId).update("isPinned", isPinned).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun toggleAllowRemix(songId: String, allowRemix: Boolean) {
        val path = "songs/$songId"
        try {
            db.collection("songs").document(songId).update("allowRemix", allowRemix).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun createPlaylist(name: String): String {
        val uid = requireUserId()
        val docRef = db.collection("playlists").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim(),
            "songIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val path = "playlists/$playlistId"
        try {
            db.collection("playlists").document(playlistId)
                .update("songIds", FieldValue.arrayUnion(songId))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val path = "playlists/$playlistId"
        try {
            db.collection("playlists").document(playlistId)
                .update("songIds", FieldValue.arrayRemove(songId))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun deletePlaylist(playlistId: String) {
        val path = "playlists/$playlistId"
        try {
            db.collection("playlists").document(playlistId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    fun getMyPlaylists(): Flow<List<Playlist>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("playlists")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Playlist::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "playlists", uid, emptyList())
        )
    }

    suspend fun generateIdeasWithAi(
        category: String? = null,
        genre: String? = null,
        mood: String? = null,
        language: String = "English",
        batchSeed: Int = 0,
        useDemoMode: Boolean = false
    ): AiIdeasResult {
        if (useDemoMode) {
            return AiIdeasResult(
                ideas = generateDemoIdeas(
                    category = category,
                    genre = genre,
                    mood = mood,
                    language = language,
                    batchSeed = batchSeed
                ),
                modelName = "demo-local-templates",
                isDemo = true
            )
        }
        return aiTextGenerator.generateIdeas(
            category = category,
            genre = genre,
            mood = mood,
            language = language,
            batchSeed = batchSeed
        )
    }

    fun generateDemoIdeas(
        category: String? = null,
        genre: String? = null,
        mood: String? = null,
        language: String = "English",
        batchSeed: Int = 0
    ): List<CreativeIdea> {
        return IdeaGeneratorEngine.generateIdeas(
            category = category,
            genre = genre,
            mood = mood,
            language = language,
            batchSeed = batchSeed
        )
    }

    suspend fun saveIdea(idea: CreativeIdea): String {
        val uid = requireUserId()
        ensureUserProfileExists()
        val docRef = db.collection("ideas").document()
        val safeTarget = when (idea.targetStudio) {
            "AUDIO", "LYRICS", "VIDEO", "MUSIC_VIDEO" -> idea.targetStudio
            else -> "AUDIO"
        }
        val payload = mapOf(
            "userId" to uid,
            "category" to idea.category.trim().ifBlank { "Song Ideas" }.take(80),
            "title" to idea.title.trim().ifBlank { "Untitled Idea" }.take(120),
            "summary" to idea.summary.trim().take(1000),
            "topic" to idea.topic.trim().take(200),
            "genre" to idea.genre.trim().take(50),
            "mood" to idea.mood.trim().take(100),
            "language" to idea.language.trim().ifBlank { "English" }.take(50),
            "tempo" to idea.tempo.trim().take(50),
            "instruments" to idea.instruments.map { it.trim() }.filter { it.isNotEmpty() }.take(20),
            "vocalStyle" to idea.vocalStyle.trim().take(100),
            "lyricsPreview" to idea.lyricsPreview.trim().take(10000),
            "videoPrompt" to idea.videoPrompt.trim().take(1000),
            "videoTemplateName" to idea.videoTemplateName.trim().take(100),
            "bibleVerse" to idea.bibleVerse.trim().take(500),
            "targetStudio" to safeTarget,
            "isDemo" to idea.isDemo,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun getMySavedIdeas(): Flow<List<CreativeIdea>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("ideas")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CreativeIdea::class.java)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, "ideas", uid, emptyList())
        )
    }

    suspend fun deleteSavedIdea(ideaId: String) {
        val path = "ideas/$ideaId"
        try {
            db.collection("ideas").document(ideaId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }
}

