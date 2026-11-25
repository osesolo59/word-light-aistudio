const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read users, songs, videos, music_videos, or playlists", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("songs").get());
  await assertFails(unauthDb.collection("videos").get());
  await assertFails(unauthDb.collection("music_videos").get());
  await assertFails(unauthDb.collection("playlists").get());
});

test("Users collection: owner can create profile with 5 credits and update allowed fields, but cannot change credits or read another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      email: "alice@example.com",
      displayName: "Alice",
      bio: "",
      photoUrl: "",
      credits: 5,
      appearance: "System Default",
      language: "English",
      isPublicProfile: true,
      createdAt: new Date(),
    })
  );

  // Cannot create profile with inflated credits
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      email: "bob@example.com",
      displayName: "Bob",
      bio: "",
      photoUrl: "",
      credits: 999,
      appearance: "System Default",
      language: "English",
      isPublicProfile: true,
      createdAt: new Date(),
    })
  );

  // Owner can update displayName/appearance
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      appearance: "Dark",
    })
  );

  // Owner cannot modify credits
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 100,
    })
  );

  // Cross-user read on /users is strictly denied (protects email & credits)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Public profiles: signed-in user can read public profile only when isPublicProfile is true", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      displayName: "Alice",
      bio: "Worship creator",
      photoUrl: "",
      isPublicProfile: true,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("public_profiles").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      displayName: "Alice",
      bio: "Worship creator",
      photoUrl: "",
      isPublicProfile: false,
      updatedAt: new Date(),
    })
  );

  await assertFails(bobDb.collection("public_profiles").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("public_profiles").doc(ALICE_UID).get());
});

test("Songs: owner can create demo song, query own songs, update allowed fields, and cross-user access is restricted", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("songs").doc("song_1").set({
      userId: ALICE_UID,
      title: "Grace Dawn",
      prompt: "Uplifting gospel hymn",
      lyrics: "Amazing grace",
      genre: "Gospel",
      audioUrl: "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
      isPublic: false,
      isDemo: true,
      isFavorite: false,
      isPinned: false,
      allowRemix: true,
      likeStatus: 0,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );

  // Non-demo or pre-published song create denied
  await assertFails(
    aliceDb.collection("songs").doc("song_bad").set({
      userId: ALICE_UID,
      title: "Bad",
      prompt: "",
      lyrics: "",
      genre: "Gospel",
      audioUrl: "https://example.com/a.mp3",
      isPublic: true,
      isDemo: false,
      isFavorite: false,
      isPinned: false,
      allowRemix: true,
      likeStatus: 0,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );

  // Owner queries own songs
  await assertSucceeds(aliceDb.collection("songs").where("userId", "==", ALICE_UID).get());

  // Unfiltered songs query fails
  await assertFails(aliceDb.collection("songs").get());

  // Bob cannot read Alice's private song or update/delete it
  await assertFails(bobDb.collection("songs").doc("song_1").get());
  await assertFails(bobDb.collection("songs").doc("song_1").update({ isFavorite: true }));
  await assertFails(bobDb.collection("songs").doc("song_1").delete());

  // Alice can publish and favorite her song, then Bob can read it via public query
  await assertSucceeds(
    aliceDb.collection("songs").doc("song_1").update({ isPublic: true, isFavorite: true })
  );
  await assertSucceeds(bobDb.collection("songs").where("isPublic", "==", true).get());
  await assertSucceeds(aliceDb.collection("songs").doc("song_1").delete());
});

test("Videos, Music Videos, and Playlists: owner-only CRUD with validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      title: "Sunrise Scene",
      prompt: "Golden sunrise",
      templateName: "Heavenly Clouds",
      videoUrl: "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
      isDemo: true,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("videos").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("videos").doc("vid_1").get());

  await assertSucceeds(
    aliceDb.collection("music_videos").doc("mv_1").set({
      userId: ALICE_UID,
      title: "Psalm 23 MV",
      songId: "song_1",
      videoId: "vid_1",
      bibleVerse: "Psalm 23:1",
      videoUrl: "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
      isDemo: true,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("music_videos").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("music_videos").doc("mv_1").get());

  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_1").set({
      userId: ALICE_UID,
      name: "Sunday Morning",
      songIds: [],
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("playlists").doc("pl_1").update({ songIds: ["song_1"] })
  );
  await assertFails(bobDb.collection("playlists").doc("pl_1").get());
  await assertFails(bobDb.collection("videos").doc("vid_1").delete());
  await assertFails(bobDb.collection("music_videos").doc("mv_1").delete());
  await assertFails(bobDb.collection("playlists").doc("pl_1").delete());

  await assertSucceeds(aliceDb.collection("videos").doc("vid_1").delete());
  await assertSucceeds(aliceDb.collection("music_videos").doc("mv_1").delete());
  await assertSucceeds(aliceDb.collection("playlists").doc("pl_1").delete());
});

test("Lyrics & Song Music Style metadata: owner can save/edit lyrics in Twi/English and save song style fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("lyrics").doc("lyric_1").set({
      userId: ALICE_UID,
      title: "Onyame Ne Hann",
      lyrics: "Onyame ne yɛn hann ne yɛn nkwa",
      topic: "Light of God",
      genre: "Highlife",
      mood: "Joyful",
      language: "Twi",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("lyrics").doc("lyric_1").update({
      title: "Onyame Ne Hann (Revised)",
      lyrics: "[Verse 1]\nOnyame ne yɛn hann ne yɛn nkwa\n[Chorus]\nAyeyi nka Wo din",
      topic: "Light of God",
      genre: "Highlife",
      mood: "Uplifting",
      language: "Mixed English + Twi",
      scriptureReference: "Psalm 27:1",
      generationSource: "AI_GEMINI",
      sectionLanguages: { "Verse 1": "Twi", "Chorus": "Mixed" },
      captionCues: [
        { id: "c1", startSec: 0, endSec: 5, sectionLabel: "Verse 1", englishText: "God is our light", twiText: "Onyame ne yɛn hann" }
      ],
    })
  );

  await assertFails(
    aliceDb.collection("lyrics").doc("lyric_1").update({
      userId: BOB_UID,
    })
  );

  await assertSucceeds(
    aliceDb.collection("lyrics").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("lyrics").doc("lyric_1").get());
  await assertFails(bobDb.collection("lyrics").doc("lyric_1").update({ title: "Hijacked Lyric" }));

  await assertSucceeds(
    aliceDb.collection("songs").doc("styled_song_1").set({
      userId: ALICE_UID,
      title: "Grace Cypher",
      prompt: "Hip-hop • English • Energetic",
      lyrics: "Walking in the light",
      genre: "Hip-hop",
      language: "English",
      mood: "Energetic",
      tempo: "Upbeat (120 BPM)",
      instruments: ["Piano", "Drums"],
      vocalStyle: "Call & Response",
      audioUrl: "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
      isPublic: false,
      isDemo: true,
      isFavorite: false,
      isPinned: false,
      allowRemix: true,
      likeStatus: 0,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );
});

test("Ideas: owner can save and delete demo or live AI ideas; cross-user access and malformed writes are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("ideas").doc("idea_1").set({
      userId: ALICE_UID,
      category: "Song Ideas",
      title: "Morning Mercies",
      summary: "Joyful Gospel worship anthem in Twi",
      topic: "New Mercies",
      genre: "Gospel",
      mood: "Joyful",
      language: "Twi",
      tempo: "Upbeat (120 BPM)",
      instruments: ["Piano", "Talking Drum"],
      vocalStyle: "Gospel Choir",
      lyricsPreview: "Onyame adɔeɛ yɛ foforɔ anɔpa biara",
      videoPrompt: "",
      videoTemplateName: "",
      bibleVerse: "Lamentations 3:22-23",
      targetStudio: "AUDIO",
      isDemo: true,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("ideas").doc("idea_live_1").set({
      userId: ALICE_UID,
      category: "Lyrics Ideas",
      title: "Live Spark Anthem",
      summary: "Generated via Gemini Developer API free tier",
      topic: "Grace and Hope",
      genre: "Worship",
      mood: "Reverent",
      language: "Ewe",
      tempo: "Slow (65 BPM)",
      instruments: ["Piano", "Strings"],
      vocalStyle: "Gospel Choir",
      lyricsPreview: "Mawu nye míaƒe kekeli",
      videoPrompt: "",
      videoTemplateName: "",
      bibleVerse: "Psalm 27:1",
      targetStudio: "LYRICS",
      isDemo: false,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("ideas").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("ideas").doc("idea_1").get());
  await assertFails(bobDb.collection("ideas").doc("idea_live_1").delete());

  await assertFails(
    aliceDb.collection("ideas").doc("idea_invalid_target").set({
      userId: ALICE_UID,
      category: "Song Ideas",
      title: "Bad Studio",
      summary: "",
      topic: "",
      genre: "Gospel",
      mood: "Joyful",
      language: "English",
      tempo: "Moderate (95 BPM)",
      instruments: [],
      vocalStyle: "Gospel Choir",
      lyricsPreview: "",
      videoPrompt: "",
      videoTemplateName: "",
      bibleVerse: "",
      targetStudio: "INVALID_STUDIO",
      isDemo: false,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("ideas").doc("idea_1").delete());
  await assertSucceeds(aliceDb.collection("ideas").doc("idea_live_1").delete());
});

test("Audio Mixes & Bilingual Metadata: owner CRUD and cross-user isolation on /audio_mixes, /lyrics, and /music_videos", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(unauthDb.collection("audio_mixes").get());

  await assertSucceeds(
    aliceDb.collection("audio_mixes").doc("mix_1").set({
      userId: ALICE_UID,
      title: "Aseda Praise Mix",
      language: "Mixed English + Twi",
      sectionLanguages: {
        "Intro": "English",
        "Verse 1": "Twi",
        "Chorus": "Mixed"
      },
      lyrics: "[Verse 1]\nOnyame ye\n[Chorus]\nGive Him praise / Ayeyi nka Wo",
      captionCues: [
        {
          id: "cue_1",
          startSec: 0,
          endSec: 4,
          sectionLabel: "Chorus",
          englishText: "Give Him praise forever",
          twiText: "Ayeyi nka Wo daa"
        }
      ],
      tracks: [
        {
          id: "t1",
          displayName: "Choir.wav",
          formatLabel: "WAV",
          localUriString: "file:///data/user/0/app/files/mixer_inputs/t1.wav",
          durationMs: 12000,
          trimStartMs: 0,
          trimEndMs: 12000,
          volume: 1.0,
          isMuted: false,
          offsetMs: 0
        }
      ],
      totalDurationMs: 12000,
      localMp3Path: "/data/user/0/app/files/mixed_audio/mix_1.mp3",
      isDemo: true,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("audio_mixes").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("audio_mixes").doc("mix_1").update({
      title: "Aseda Praise Mix v2",
      language: "Mixed English + Twi",
      lyrics: "[Chorus]\nAyeyi nka Wo",
      tracks: [],
      totalDurationMs: 8000,
      localMp3Path: "/data/user/0/app/files/mixed_audio/mix_1_v2.mp3",
    })
  );

  await assertFails(bobDb.collection("audio_mixes").doc("mix_1").get());
  await assertFails(bobDb.collection("audio_mixes").doc("mix_1").update({ title: "Hijacked" }));
  await assertFails(bobDb.collection("audio_mixes").doc("mix_1").delete());

  await assertSucceeds(
    aliceDb.collection("lyrics").doc("lyric_bi_1").set({
      userId: ALICE_UID,
      title: "Yesu Do",
      lyrics: "[Verse 1]\nMepaakyɛw Nyame\n[Chorus]\nJesus loves me",
      topic: "Grace",
      genre: "Worship",
      mood: "Uplifting",
      language: "Mixed English + Twi",
      sectionLanguages: { "Verse 1": "Twi", "Chorus": "English" },
      captionCues: [
        { id: "c1", startSec: 0, endSec: 5, sectionLabel: "Verse 1", englishText: "God is good", twiText: "Onyame ye" }
      ],
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("music_videos").doc("mv_bi_1").set({
      userId: ALICE_UID,
      title: "Aseda MV",
      songId: "Aseda Praise Mix",
      videoId: "Uploaded Photos (2)",
      bibleVerse: "Psalm 100:4",
      videoUrl: "file:///data/user/0/app/files/exported_videos/mv.mp4",
      aspectRatio: "16:9",
      captions: "Ayeyi nka Wo / Praise the Lord",
      captionDisplayMode: "BOTH",
      captionCues: [
        { id: "c1", startSec: 0, endSec: 3, sectionLabel: "Verse 1", englishText: "Give thanks", twiText: "Da Onyame ase" }
      ],
      isDemo: true,
      creatorName: "Alice",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("audio_mixes").doc("mix_1").delete());
  await assertSucceeds(aliceDb.collection("lyrics").doc("lyric_bi_1").delete());
  await assertSucceeds(aliceDb.collection("music_videos").doc("mv_bi_1").delete());
});

test("Creative Projects Gallery: owner CRUD, filtered query, immutable fields, and negative security bounds on /creative_projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const past = new Date(Date.now() - 2000);
  const basePayload = {
    userId: ALICE_UID,
    title: "Aseda Praise Anthem",
    genre: "Gospel",
    projectType: "SONG",
    language: "Mixed English + Twi",
    description: "Sunday worship opening song in Royal Purple & Gold branding",
    status: "IN_PROGRESS",
    favorite: false,
    coverTheme: "PURPLE_GOLD",
    linkedSourceCollection: "",
    linkedSourceId: "",
    createdAt: past,
    updatedAt: past,
  };

  // Unauthenticated read/list/create must fail
  await assertFails(unauthDb.collection("creative_projects").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("creative_projects").doc("proj_1").set(basePayload));

  // Valid owner create matching Kotlin payload succeeds
  await assertSucceeds(aliceDb.collection("creative_projects").doc("proj_1").set(basePayload));

  // Filtered owner list query succeeds; unfiltered query fails
  await assertSucceeds(aliceDb.collection("creative_projects").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("creative_projects").get());

  // Cross-user get, list, update, delete fail
  await assertFails(bobDb.collection("creative_projects").doc("proj_1").get());
  await assertFails(bobDb.collection("creative_projects").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("creative_projects").doc("proj_1").update({ title: "Hijacked" }));
  await assertFails(bobDb.collection("creative_projects").doc("proj_1").delete());

  // Partial update on allowed mutable fields succeeds (createdAt preserved)
  await assertSucceeds(
    aliceDb.collection("creative_projects").doc("proj_1").update({
      title: "Aseda Praise Anthem (Mastered)",
      genre: "Highlife",
      favorite: true,
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt or userId mutation fails
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_1").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Ghost field & @DocumentId 'id' key rejected on create/update
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_with_id").set({
      ...basePayload,
      id: "proj_with_id",
    })
  );
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_1").update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Future timestamp & invalid enum rejected
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_future").set({
      ...basePayload,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    aliceDb.collection("creative_projects").doc("proj_bad_type").set({
      ...basePayload,
      projectType: "UNKNOWN_TYPE",
    })
  );

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("creative_projects").doc("proj_1").delete());
});




