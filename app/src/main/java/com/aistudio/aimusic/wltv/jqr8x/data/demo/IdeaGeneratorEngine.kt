package com.aistudio.aimusic.wltv.jqr8x.data.demo

import com.aistudio.aimusic.wltv.jqr8x.data.model.CreativeIdea

object IdeaGeneratorEngine {

    val CATEGORIES = listOf(
        "Song Ideas",
        "Lyrics Ideas",
        "Music Style Ideas",
        "Gospel Worship Ideas",
        "Praise and Reggae Ideas",
        "Music Video Ideas",
        "Christian Video Ideas"
    )

    val LANGUAGES = listOf("English", "Twi", "Mixed English + Twi", "Akan", "Ga", "Ewe")

    val OPTIONAL_GENRES = listOf(
        "Gospel", "Worship", "Praise", "Reggae", "Highlife", "Hip-hop", "Afrobeat", "Contemporary"
    )

    val OPTIONAL_MOODS = listOf(
        "Uplifting", "Joyful", "Reverent", "Peaceful", "Soulful", "Energetic"
    )

    private data class LocalizedPhrase(
        val titlePrefix: String,
        val verseLine1: String,
        val verseLine2: String,
        val chorusLine1: String,
        val chorusLine2: String
    )

    private data class SeedConcept(
        val theme: String,
        val verseRef: String,
        val verseText: String,
        val defaultGenre: String,
        val defaultMood: String,
        val tempo: String,
        val instruments: List<String>,
        val vocalStyle: String,
        val visualScene: String,
        val videoTemplate: String,
        val phrases: Map<String, LocalizedPhrase>
    ) {
        fun phraseFor(language: String): LocalizedPhrase {
            if (language.equals("Mixed English + Twi", ignoreCase = true)) {
                val eng = phrases["English"]!!
                val twi = phrases["Twi"] ?: eng
                return LocalizedPhrase(
                    titlePrefix = "${twi.titlePrefix} (${eng.titlePrefix})",
                    verseLine1 = "${twi.verseLine1} / ${eng.verseLine1}",
                    verseLine2 = "${twi.verseLine2} / ${eng.verseLine2}",
                    chorusLine1 = "${eng.chorusLine1} / ${twi.chorusLine1}",
                    chorusLine2 = "${eng.chorusLine2} / ${twi.chorusLine2}"
                )
            }
            return phrases[language] ?: phrases["English"]!!
        }
    }

    private val seedConcepts = listOf(
        SeedConcept(
            theme = "Morning Light & Mercies",
            verseRef = "Lamentations 3:22-23",
            verseText = "His mercies never come to an end; they are new every morning.",
            defaultGenre = "Gospel",
            defaultMood = "Uplifting",
            tempo = "Moderate (95 BPM)",
            instruments = listOf("Piano", "Strings", "Gospel Choir").filter { it != "Gospel Choir" } + "Organ",
            vocalStyle = "Gospel Choir",
            visualScene = "Golden sunrise rays breaking over quiet hills with warm golden dust motes and sanctuary silhouettes",
            videoTemplate = "Morning Worship",
            phrases = mapOf(
                "English" to LocalizedPhrase("Dawn of Mercy", "Before the sun breaks through the sky", "Your steadfast mercy lifts my eyes", "Every morning brings Your light", "Word and grace make all things bright"),
                "Twi" to LocalizedPhrase("Anɔpa Mmɔborɔhunu", "Anɔpa biara Wo hann pue", "Wo mmɔborɔhunu yɛ foforɔ daa", "Onyame ayeyi nka Wo din", "Wo asɛm hyerɛn wɔ yɛn so"),
                "Akan" to LocalizedPhrase("Adekyeeɛ Adom", "Adekyeeɛ foforɔ mu yɛhu Wo dɔ", "Wode asomdwoeɛ ka yɛn ho", "Ayeyi nka Tumfoɔ Nyankopɔn", "Wo hann kyerɛ yɛn kwan"),
                "Ga" to LocalizedPhrase("Leebi Mɔbɔnalɛ", "Leebi fɛɛ leebi Ola tsoɔ", "Omɔbɔnalɛ lɛ hiɔ shi daa", "Wɔjieɔ Oyi yɛ miishɛɛ mli", "Nyɔŋmɔ wiemɔ lɛ ji wɔla"),
                "Ewe" to LocalizedPhrase("Ŋdi Nublanuikpɔkpɔ", "Ŋdi sia ŋdi Wò kekeli dzena", "Wò nublanuikpɔkpɔ mewua enu o", "Míekafua Wò, Aƒetɔ Mawu", "Wò nya la naa agbe mí")
            )
        ),
        SeedConcept(
            theme = "Rivers of Peace",
            verseRef = "John 14:27",
            verseText = "Peace I leave with you; My peace I give to you.",
            defaultGenre = "Worship",
            defaultMood = "Peaceful",
            tempo = "Slow (65 BPM)",
            instruments = listOf("Piano", "Acoustic Guitar", "Strings"),
            vocalStyle = "Solo Female",
            visualScene = "Slow crystal river reflecting violet twilight clouds with soft scripture typography rising gently",
            videoTemplate = "Nature Praise",
            phrases = mapOf(
                "English" to LocalizedPhrase("Still Waters Anthem", "When the noise grows loud around my soul", "Your gentle whisper makes me whole", "Peace like a river flowing deep", "In Your presence I will keep"),
                "Twi" to LocalizedPhrase("Asomdwoeɛ Asubɔnten", "Sɛ wiase gyegyeegye sɔre a", "Wo asomdwoeɛ ma me kra dwo", "Yesu yɛ me guankɔbea daa", "Wɔ Wo anim na mehome"),
                "Akan" to LocalizedPhrase("Asomdwoeɛ Nsuo", "Wo nne bɛkasa wɔ ahum no mu", "Me kra nya ahomegyeɛ wɔ Wo mu", "Aseda ne ayeyi nka Wo", "Wo nsa kura me yie"),
                "Ga" to LocalizedPhrase("Toiŋjɔlɛ Faa", "Kɛji ahum lɛ te shi yɛ mishihilɛ mli", "Oha mi toiŋjɔlɛ ni sɛɛ efooo", "Wɔnyɛɔ wɔhiɛ wɔkaa Bo", "Nuŋtsɔ, Oji wɔhebuuhe"),
                "Ewe" to LocalizedPhrase("Ŋutifafa Tɔsisi", "Ne xexeame ƒe ahom tu gɔ̃ hã", "Wò ŋutifafa dzɔa nye luʋɔ dzi", "Aƒetɔ, Wòe nye nye sitsoƒe", "Mele ɖiɖi me le Wò gbɔ")
            )
        ),
        SeedConcept(
            theme = "Joyful Thanksgiving Procession",
            verseRef = "Psalm 100:4",
            verseText = "Enter His gates with thanksgiving and His courts with praise.",
            defaultGenre = "Highlife",
            defaultMood = "Joyful",
            tempo = "Upbeat (120 BPM)",
            instruments = listOf("Acoustic Guitar", "Bass", "Talking Drum", "Saxophone"),
            vocalStyle = "Call & Response",
            visualScene = "Vibrant celebratory sanctuary choir clapping in golden spotlights with joyful purple ribbons",
            videoTemplate = "Church Event",
            phrases = mapOf(
                "English" to LocalizedPhrase("Gates of Praise", "Lift a shout of gratitude today", "Dance in freedom along His way", "Hallelujah, raise the sound", "Where His grace and joy abound"),
                "Twi" to LocalizedPhrase("Aseda Ponkɛseɛ", "Momma yɛnto aseda dwom nnɛ", "Ɔhene Yesu adi nkonim ama yɛn", "Yɛbɔ nsam ma Ne din so", "Onyame ayɛ bi ama yɛn"),
                "Akan" to LocalizedPhrase("Anigyeɛ Aseda", "Fa anigyeɛ bra Ne fie mu", "Bɔ sankuo na hyira Ne din", "Ayeyi wura fata tumi nyinaa", "Yɛrebɔ ose ama Awurade"),
                "Ga" to LocalizedPhrase("Miishɛɛ Yijiemɔ", "Nyɛbaa Eshia lɛ mli kɛ shidaa", "Wɔlaa lala wɔwoɔ Egbɛi nɔ", "Haleluya, Nuŋtsɔ lɛ ehi", "Esuɔmɔ lɛ tekeɔ nɔ daa"),
                "Ewe" to LocalizedPhrase("Dzidzɔ Akpedada", "Míatsɔ akpedada age ɖe Eƒe xɔ me", "Míadzi ha kple sanku na Fia la", "Akpe na Wò, Mawu Nyuie", "Wò lɔlɔ̃ li tso mavɔ me")
            )
        ),
        SeedConcept(
            theme = "Zion Roots & Steadfast Hope",
            verseRef = "Psalm 121:1-2",
            verseText = "My help comes from the Lord, who made heaven and earth.",
            defaultGenre = "Reggae",
            defaultMood = "Soulful",
            tempo = "Moderate (95 BPM)",
            instruments = listOf("Bass", "Organ", "Drums", "Acoustic Guitar"),
            vocalStyle = "Solo Male",
            visualScene = "Warm ocean horizon under amber clouds with steady shoreline waves and acoustic silhouette",
            videoTemplate = "Scripture Motion",
            phrases = mapOf(
                "English" to LocalizedPhrase("Roots in Zion", "One drop rhythm praising the Most High", "Faith rises higher than the sky", "My help comes from the Maker of all", "Standing strong, I will not fall"),
                "Twi" to LocalizedPhrase("Gyidie Nnyinasoɔ", "Mehwɛ mmepɔ no soɔ daa nyinaa", "Me mmoa firi Awurade hɔ", "Ɔno na ɔbɔɔ ɔsoro ne asaase", "Me gyidie remmrɛ da"),
                "Akan" to LocalizedPhrase("Sion Anidasoɔ", "Awurade ne me botantim ne m'ahoɔden", "Mɛto dwom ama Ne din kronkron", "Ɔhwɛ me so awia ne anadwo", "Nkonimdie wɔ Ne nsam"),
                "Ga" to LocalizedPhrase("Sion Hiɛnɔkamɔ", "Mibɛma mihiɛ nɔ mayaa gɔji lɛ anɔ", "Miyelikɛbuamɔ jɛ Nuŋtsɔ lɛ ŋɔɔ", "Lɛ ebɔ ŋwɛi kɛ shikpɔŋ", "Mikɛ gbee ni wa baajie Eyi"),
                "Ewe" to LocalizedPhrase("Sion Mɔkpɔkpɔ", "Mafɔ nye ŋkuwo ɖe towo dzi", "Nye kpekpeɖeŋu tsoa Aƒetɔ gbɔ", "Eyae wɔ dziƒo kple anyigba", "Maka ɖe Edzi le nye agbe me")
            )
        ),
        SeedConcept(
            theme = "Unstoppable Light in the City",
            verseRef = "John 1:5",
            verseText = "The light shines in the darkness, and the darkness has not overcome it.",
            defaultGenre = "Praise",
            defaultMood = "Energetic",
            tempo = "Fast (140 BPM)",
            instruments = listOf("Drums", "Bass", "Piano", "Saxophone"),
            vocalStyle = "Duet",
            visualScene = "Aerial twilight city skyline transforming into radiant violet and gold light trails",
            videoTemplate = "City Grace",
            phrases = mapOf(
                "English" to LocalizedPhrase("City Light Victory", "Turn the volume up for the King of Light", "Shining through the shadows of the night", "Unstoppable grace, brighter than the dawn", "By His Word we carry on"),
                "Twi" to LocalizedPhrase("Hann Di Nkonim", "Hann no hyerɛn wɔ esum kabii mu", "Esum ntumi nsi Ne kwan da", "Yɛredi ahurisie wɔ Awurade mu", "Wo asɛm yɛ kanea ma yɛn nan"),
                "Akan" to LocalizedPhrase("Kuro Mu Hann", "Ma Wo hann nhyerɛn kuro yi so", "Yɛretwe adwene nyinaa aba Wo so", "Nkonimdie asɛm reteteɛ", "Yesu din boro din nyinaa so"),
                "Ga" to LocalizedPhrase("La Yɛ Duŋ Mli", "La lɛ miitso yɛ duŋ lɛ mli", "Duŋ lɛ nyɛŋ ashwie lɛ", "Wɔkɛ miishɛɛ miishi Nuŋtsɔ lɛ", "Ewiemɔ lɛ ji wɔnanehena kanea"),
                "Ewe" to LocalizedPhrase("Kekeli Le Viviti Me", "Kekeli la le klẽm le viviti me", "Viviti mate ŋu atsi enu o", "Míele dzidzɔ kpɔm le Aƒetɔ me", "Wò nya la nye akaɖi na mí")
            )
        ),
        SeedConcept(
            theme = "Covenant Shelter & Trust",
            verseRef = "Psalm 91:1-2",
            verseText = "Whoever dwells in the shelter of the Most High will rest in the shadow of the Almighty.",
            defaultGenre = "Worship",
            defaultMood = "Reverent",
            tempo = "Slow (65 BPM)",
            instruments = listOf("Piano", "Strings", "Organ"),
            vocalStyle = "Gospel Choir",
            visualScene = "Soft cathedral light beams filtering through stained glass in deep royal purple and gold tones",
            videoTemplate = "Scripture Motion",
            phrases = mapOf(
                "English" to LocalizedPhrase("Under His Wings", "Quiet sanctuary where fear fades away", "Wrapped in covenant grace today", "Holy, holy is the Lord Almighty", "My refuge and my fortress mighty"),
                "Twi" to LocalizedPhrase("Otumfoɔ Nwini Ase", "Nea ɔte Ɔsorosoro no hintabea", "Ɔbɛhome wɔ Otumfoɔ nwini ase", "Awurade, Woyɛ me guankɔbea", "Mekoto sɔre Wo wɔ nokware mu"),
                "Akan" to LocalizedPhrase("Kronkron Hintabea", "Wo nwini ase na m'akoma tɔ yam", "Me gyidie gyina Wo bɔhyɛ so", "Kronkron ne Awurade, asafo Tumfoɔ", "Mɛtena Wo fie daa nyinaa"),
                "Ga" to LocalizedPhrase("Mɔ Ni Yɔɔ Ŋwɛi", "Mɔ ni hiɔ Mɔ Ni Yɔɔ Ŋwɛi lɛ teemɔhe", "Ebaajɔɔ ehe yɛ Ofe lɛ hɔɔŋ shishi", "Nuŋtsɔ, Oji mihɔɔŋ kɛ mimɔɔ", "Wɔkotoɔ wɔjaa Bo yɛ krɔŋkrɔŋfeemɔ mli"),
                "Ewe" to LocalizedPhrase("Dziƒoʋĩtɔ Ƒe Vɔvɔli Te", "Ame si nɔa Dziƒoʋĩtɔ ƒe bebefe", "Dzudzɔa eɖokui le Ŋusẽkatãtɔ ƒe vɔvɔli te", "Aƒetɔ, Wòe nye nye mɔ́ sesẽ", "Míedzea klo subɔa Wò le nyateƒe me")
            )
        ),
        SeedConcept(
            theme = "Living Word Harvest Anthem",
            verseRef = "Psalm 119:105",
            verseText = "Your word is a lamp to my feet and a light to my path.",
            defaultGenre = "Hip-hop",
            defaultMood = "Energetic",
            tempo = "Upbeat (120 BPM)",
            instruments = listOf("Drums", "Piano", "Talking Drum", "Bass"),
            vocalStyle = "Call & Response",
            visualScene = "Kinetic golden scripture lines glowing across a dark purple stage with rhythmic pulse waves",
            videoTemplate = "City Grace",
            phrases = mapOf(
                "English" to LocalizedPhrase("Lamp to My Path", "Scripture on my lips, rhythm in my stride", "Every step guided by the Word inside", "Lamp to my feet, light upon my road", "Lifting off every heavy load"),
                "Twi" to LocalizedPhrase("Asɛm Yɛ Kanea", "Wo asɛm yɛ kanea ma me nan", "Hann biara firi Wo nkyɛn ba", "Yɛde akokoduru bɔ asɛmpa no dawuro", "Word and Light kyerɛ yɛn kwan"),
                "Akan" to LocalizedPhrase("Kanea Wɔ Kwan So", "Anammɔn biara gyina asɛm no so", "Yɛrenya nkɔsoɔ wɔ Awurade din mu", "Bɔ sankuo na kae Ne papayɛ", "Hann no redɛre wɔ yɛn mu"),
                "Ga" to LocalizedPhrase("Wiemɔ Ji Kanea", "Owiemɔ lɛ ji kanea ha minaji", "Kɛ la yɛ migbɛ lɛ nɔ", "Wɔshiɛɔ sanekpakpa lɛ kɛ miishɛɛ", "Nuŋtsɔ lɛ la lɛ miikudɔ wɔ"),
                "Ewe" to LocalizedPhrase("Nya La Nye Akaɖi", "Wò nya la nye akaɖi na nye afɔwo", "Kple kekeli na nye mɔ dzi", "Míele dzidzɔ me le Aƒetɔ ƒe mɔ dzi", "Wò nya la naa ŋusẽ mí")
            )
        )
    )

    fun generateIdeas(
        category: String? = null,
        genre: String? = null,
        mood: String? = null,
        language: String = "English",
        batchSeed: Int = 0
    ): List<CreativeIdea> {
        val safeLanguage = LANGUAGES.firstOrNull { it.equals(language.trim(), ignoreCase = true) } ?: "English"
        val selectedCategory = category?.takeIf { it in CATEGORIES }
        val offset = ((batchSeed % seedConcepts.size) + seedConcepts.size) % seedConcepts.size

        return List(5) { index ->
            val concept = seedConcepts[(offset + index * 2) % seedConcepts.size]
            val resolvedCategory = selectedCategory ?: CATEGORIES[(batchSeed + index) % CATEGORIES.size]
            buildIdea(
                index = index,
                batchSeed = batchSeed,
                category = resolvedCategory,
                optionalGenre = genre?.takeIf { it.isNotBlank() },
                optionalMood = mood?.takeIf { it.isNotBlank() },
                language = safeLanguage,
                concept = concept
            )
        }
    }

    private fun buildIdea(
        index: Int,
        batchSeed: Int,
        category: String,
        optionalGenre: String?,
        optionalMood: String?,
        language: String,
        concept: SeedConcept
    ): CreativeIdea {
        val phrase = concept.phraseFor(language)
        val variantTag = if (batchSeed > 0) " (Mix ${batchSeed + 1}.${index + 1})" else ""

        val defaultCategoryGenre = when (category) {
            "Gospel Worship Ideas" -> if (index % 2 == 0) "Worship" else "Gospel"
            "Praise and Reggae Ideas" -> if (index % 2 == 0) "Reggae" else "Praise"
            else -> concept.defaultGenre
        }
        val resolvedGenre = optionalGenre ?: defaultCategoryGenre

        val defaultCategoryMood = when (category) {
            "Gospel Worship Ideas" -> "Reverent"
            "Praise and Reggae Ideas" -> if (index % 2 == 0) "Joyful" else "Soulful"
            else -> concept.defaultMood
        }
        val resolvedMood = optionalMood ?: defaultCategoryMood

        val resolvedTempo = when (resolvedGenre) {
            "Worship" -> "Slow (65 BPM)"
            "Reggae" -> "Moderate (95 BPM)"
            "Praise", "Highlife", "Afrobeat", "Hip-hop" -> "Upbeat (120 BPM)"
            else -> concept.tempo
        }

        val resolvedInstruments = when (resolvedGenre) {
            "Reggae" -> listOf("Bass", "Organ", "Drums", "Acoustic Guitar")
            "Highlife", "Praise" -> listOf("Acoustic Guitar", "Talking Drum", "Saxophone", "Drums")
            "Worship" -> listOf("Piano", "Strings", "Organ")
            "Hip-hop" -> listOf("Drums", "Piano", "Bass", "Talking Drum")
            else -> concept.instruments
        }

        val lyricsBody = """
            |[Verse 1 - $resolvedGenre | $language]
            |${phrase.verseLine1}
            |${phrase.verseLine2}
            |
            |[Chorus]
            |${phrase.chorusLine1}
            |${phrase.chorusLine2}
            |
            |(${concept.verseRef}: ${concept.verseText})
        """.trimMargin()

        return when (category) {
            "Lyrics Ideas" -> CreativeIdea(
                id = "idea_${batchSeed}_${index}_${resolvedCategoryKey(category)}",
                category = category,
                title = "${phrase.titlePrefix}$variantTag",
                summary = "$language $resolvedGenre lyric hook rooted in ${concept.verseRef} with a $resolvedMood chorus.",
                topic = concept.theme,
                genre = resolvedGenre,
                mood = resolvedMood,
                language = language,
                tempo = resolvedTempo,
                instruments = resolvedInstruments,
                vocalStyle = concept.vocalStyle,
                lyricsPreview = lyricsBody,
                bibleVerse = "${concept.verseRef} — ${concept.verseText}",
                targetStudio = "LYRICS",
                isDemo = true
            )

            "Music Style Ideas" -> CreativeIdea(
                id = "idea_${batchSeed}_${index}_${resolvedCategoryKey(category)}",
                category = category,
                title = "$resolvedGenre $resolvedMood Blueprint: ${phrase.titlePrefix}$variantTag",
                summary = "$resolvedTempo groove featuring ${resolvedInstruments.joinToString(", ")} with ${concept.vocalStyle} vocals in $language.",
                topic = concept.theme,
                genre = resolvedGenre,
                mood = resolvedMood,
                language = language,
                tempo = resolvedTempo,
                instruments = resolvedInstruments,
                vocalStyle = concept.vocalStyle,
                lyricsPreview = lyricsBody,
                bibleVerse = "${concept.verseRef} — ${concept.verseText}",
                targetStudio = "AUDIO",
                isDemo = true
            )

            "Music Video Ideas" -> CreativeIdea(
                id = "idea_${batchSeed}_${index}_${resolvedCategoryKey(category)}",
                category = category,
                title = "${phrase.titlePrefix} (MV Concept)$variantTag",
                summary = "Pair a $language $resolvedGenre track with ${concept.visualScene} and golden scripture overlays (${concept.verseRef}).",
                topic = concept.theme,
                genre = resolvedGenre,
                mood = resolvedMood,
                language = language,
                tempo = resolvedTempo,
                instruments = resolvedInstruments,
                vocalStyle = concept.vocalStyle,
                videoPrompt = concept.visualScene,
                videoTemplateName = concept.videoTemplate,
                bibleVerse = "${concept.verseRef}: \"${concept.verseText}\"",
                targetStudio = "MUSIC_VIDEO",
                isDemo = true
            )

            "Christian Video Ideas" -> CreativeIdea(
                id = "idea_${batchSeed}_${index}_${resolvedCategoryKey(category)}",
                category = category,
                title = "${phrase.titlePrefix} Visual Story$variantTag",
                summary = "${concept.visualScene}. Ideal for $language devotional reflection (${concept.verseRef}).",
                topic = concept.theme,
                genre = resolvedGenre,
                mood = resolvedMood,
                language = language,
                tempo = resolvedTempo,
                instruments = resolvedInstruments,
                vocalStyle = concept.vocalStyle,
                videoPrompt = "${concept.visualScene}. Theme: ${concept.theme} ($language, $resolvedMood). Scripture overlay: ${concept.verseRef}.",
                videoTemplateName = concept.videoTemplate,
                bibleVerse = "${concept.verseRef}: \"${concept.verseText}\"",
                targetStudio = "VIDEO",
                isDemo = true
            )

            else -> CreativeIdea(
                // "Song Ideas", "Gospel Worship Ideas", "Praise and Reggae Ideas"
                id = "idea_${batchSeed}_${index}_${resolvedCategoryKey(category)}",
                category = category,
                title = "${phrase.titlePrefix}$variantTag",
                summary = "A $resolvedMood $language $resolvedGenre song inspired by ${concept.verseRef} with ${concept.vocalStyle} harmony.",
                topic = concept.theme,
                genre = resolvedGenre,
                mood = resolvedMood,
                language = language,
                tempo = resolvedTempo,
                instruments = resolvedInstruments,
                vocalStyle = concept.vocalStyle,
                lyricsPreview = lyricsBody,
                bibleVerse = "${concept.verseRef} — ${concept.verseText}",
                targetStudio = "AUDIO",
                isDemo = true
            )
        }
    }

    private fun resolvedCategoryKey(category: String): String =
        category.lowercase().replace(" ", "_")

    fun formatForClipboard(idea: CreativeIdea): String = buildString {
        appendLine("Word & Light AI STUDIO — ${idea.category}")
        appendLine("Title: ${idea.title}")
        appendLine("Language: ${idea.language} | Genre: ${idea.genre} | Mood: ${idea.mood}")
        appendLine("Style: ${idea.tempo} | Vocal: ${idea.vocalStyle}")
        if (idea.instruments.isNotEmpty()) {
            appendLine("Instruments: ${idea.instruments.joinToString(", ")}")
        }
        appendLine("Summary: ${idea.summary}")
        if (idea.lyricsPreview.isNotBlank()) {
            appendLine()
            appendLine("Lyrics Idea:")
            appendLine(idea.lyricsPreview)
        }
        if (idea.videoPrompt.isNotBlank()) {
            appendLine()
            appendLine("Video Prompt (${idea.videoTemplateName}): ${idea.videoPrompt}")
        }
        if (idea.bibleVerse.isNotBlank()) {
            appendLine("Scripture: ${idea.bibleVerse}")
        }
    }.trim()
}
