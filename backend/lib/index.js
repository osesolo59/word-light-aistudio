"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.initializeUser = exports.generateLyrics = exports.togglePublicSharing = exports.deleteSong = exports.generateMusic = exports.STRICT_FREE_DEMO_MODE = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
admin.initializeApp();
const REGION = 'europe-west2';
/**
 * Server-enforced Strict Free Demo Mode flag.
 * Client-supplied `isDemo` parameters are ignored so paid Gemini/Lyria APIs can never be invoked.
 */
exports.STRICT_FREE_DEMO_MODE = true;
const DEMO_AUDIO_URL = 'https://storage.googleapis.com/exoplayer-test-media-0/play.mp3';
/**
 * Validation Helpers
 */
const isValidTaskId = (id) => typeof id === 'string' && id.length >= 20 && id.length <= 64 && /^[a-zA-Z0-9-_]+$/.test(id);
const validateString = (val, min, max) => typeof val === 'string' && val.trim().length >= min && val.trim().length <= max;
const buildDemoLyrics = (topic, genre, mood, language) => {
    const cleanTopic = topic.trim();
    const cleanGenre = genre.trim() || 'Worship';
    const cleanMood = mood.trim() || 'Uplifting';
    const cleanLanguage = language.trim() || 'English';
    switch (cleanLanguage.toLowerCase()) {
        case 'twi':
        case 'akan':
            return `[Demo Lyrics — ${cleanGenre} | ${cleanLanguage} | ${cleanMood}]\nTitle/Topic: ${cleanTopic}\n\n[Verse 1]\nOnyame ne yɛn hann ne yɛn nkwa (${cleanTopic})\nYɛma Wo din so wɔ anigye mu\n\n[Chorus]\nAyeyi nka Wo, Awurade\nWord & Light gyina yɛn nkyɛn\n\n(AI generation disabled in Strict Free Demo Mode)`;
        case 'ga':
            return `[Demo Lyrics — ${cleanGenre} | ${cleanLanguage} | ${cleanMood}]\nTitle/Topic: ${cleanTopic}\n\n[Verse 1]\nNyɔŋmɔ ji wɔ la kɛ wɔ wala (${cleanTopic})\nWɔjieɔ Oyi yɛ miishɛɛ mli\n\n[Chorus]\nYijiemɔ aha Bo, Nuŋtsɔ\nWord & Light damɔ wɔmasɛi\n\n(AI generation disabled in Strict Free Demo Mode)`;
        case 'ewe':
            return `[Demo Lyrics — ${cleanGenre} | ${cleanLanguage} | ${cleanMood}]\nTitle/Topic: ${cleanTopic}\n\n[Verse 1]\nMawu nye míaƒe kekeli kple agbe (${cleanTopic})\nMíekafua Wò ŋkɔ le dzidzɔ me\n\n[Chorus]\nKafukafu na Wò, Aƒetɔ\nWord & Light le mía gbɔ\n\n(AI generation disabled in Strict Free Demo Mode)`;
        default:
            return `[Demo Lyrics — ${cleanGenre} | ${cleanLanguage} | ${cleanMood}]\nTopic: ${cleanTopic}\n\n[Verse 1]\nWalking in the promise of ${cleanTopic}\nEvery note rises in a ${cleanMood} spirit\n\n[Chorus]\nShine Your light across the night\nWord and Light, our strength and song\n\n(AI generation disabled in Strict Free Demo Mode)`;
    }
};
/**
 * MUSIC GENERATION — Server-Enforced Strict Free Demo Mode (No Lyria / Gemini / Blaze Billing)
 */
exports.generateMusic = functions.region(REGION).https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Access denied. Please sign in.');
    }
    const { title, prompt = '', lyrics = '', genre, modelId, taskId } = data || {};
    if (!isValidTaskId(taskId)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid or missing Task ID.');
    }
    if (!validateString(title, 1, 100)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid title length.');
    }
    if (!validateString(prompt, 0, 1000)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid style prompt length.');
    }
    if (!validateString(lyrics, 0, 10000)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid lyrics length.');
    }
    if (!validateString(genre, 1, 50)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid genre.');
    }
    if (modelId !== 'lyria-3.5' && modelId !== 'lyria-3-clip-preview' && modelId !== 'demo-full' && modelId !== 'demo-clip') {
        throw new functions.https.HttpsError('invalid-argument', 'Unsupported music model.');
    }
    if (!exports.STRICT_FREE_DEMO_MODE) {
        throw new functions.https.HttpsError('failed-precondition', 'Paid AI music generation is disabled on this server.');
    }
    // Always return a safe server-enforced demo track regardless of client `data.isDemo`
    return {
        audioUrl: DEMO_AUDIO_URL,
        songId: `demo_${Date.now()}`,
        isDemo: true,
        strictDemoMode: true,
    };
});
/**
 * SECURE SONG DELETION
 */
exports.deleteSong = functions.region(REGION).https.onCall(async (data, context) => {
    var _a;
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const { songId } = data || {};
    if (!validateString(songId, 1, 100)) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid Song ID.');
    }
    const db = admin.firestore();
    const songRef = db.collection('songs').doc(songId);
    const songDoc = await songRef.get();
    if (!songDoc.exists) {
        throw new functions.https.HttpsError('not-found', 'Song not found.');
    }
    if (((_a = songDoc.data()) === null || _a === void 0 ? void 0 : _a.userId) !== context.auth.uid) {
        throw new functions.https.HttpsError('permission-denied', 'Unauthorized.');
    }
    await songRef.delete();
    return { success: true };
});
/**
 * TOGGLE PUBLIC SHARING
 */
exports.togglePublicSharing = functions.region(REGION).https.onCall(async (data, context) => {
    var _a;
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const { songId, isPublic } = data || {};
    if (!validateString(songId, 1, 100) || typeof isPublic !== 'boolean') {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid params.');
    }
    const db = admin.firestore();
    const songRef = db.collection('songs').doc(songId);
    const songDoc = await songRef.get();
    if (!songDoc.exists) {
        throw new functions.https.HttpsError('not-found', 'Song not found.');
    }
    if (((_a = songDoc.data()) === null || _a === void 0 ? void 0 : _a.userId) !== context.auth.uid) {
        throw new functions.https.HttpsError('permission-denied', 'Unauthorized.');
    }
    await songRef.update({ isPublic });
    return { success: true };
});
/**
 * LYRICS GENERATION — Server-Enforced Strict Free Demo Mode (No Gemini API Calls)
 */
exports.generateLyrics = functions.region(REGION).https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const { topic, genre = 'Worship', mood = 'Uplifting', language = 'English' } = data || {};
    if (!validateString(topic, 1, 200)) {
        throw new functions.https.HttpsError('invalid-argument', 'Topic too long or empty.');
    }
    if (!exports.STRICT_FREE_DEMO_MODE) {
        throw new functions.https.HttpsError('failed-precondition', 'Paid AI lyrics generation is disabled on this server.');
    }
    return {
        lyrics: buildDemoLyrics(topic, typeof genre === 'string' ? genre : 'Worship', typeof mood === 'string' ? mood : 'Uplifting', typeof language === 'string' ? language : 'English'),
        isDemo: true,
        strictDemoMode: true,
    };
});
/**
 * NEW USER INITIALIZATION
 */
exports.initializeUser = functions.region(REGION).auth.user().onCreate(async (user) => {
    const db = admin.firestore();
    const userRef = db.collection('users').doc(user.uid);
    try {
        await db.runTransaction(async (transaction) => {
            const snap = await transaction.get(userRef);
            if (snap.exists)
                return;
            transaction.set(userRef, {
                uid: user.uid,
                email: user.email || '',
                displayName: user.displayName || 'New User',
                credits: 5,
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            transaction.set(db.collection('ledger').doc(), {
                userId: user.uid,
                amount: 5,
                reason: 'PROMOTIONAL_GIFT',
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
            });
        });
    }
    catch (error) {
        console.error('Initialization error:', error);
    }
});