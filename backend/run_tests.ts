import fft from 'firebase-functions-test';
import { generateMusic, generateLyrics, STRICT_FREE_DEMO_MODE } from './index';

const testEnv = fft();

async function runSecurityTests() {
  console.log('--- STARTING STRICT FREE DEMO MODE SECURITY TESTS ---');
  const wrappedMusic = testEnv.wrap(generateMusic);
  const wrappedLyrics = testEnv.wrap(generateLyrics);
  const uid = 'owner-user-' + Date.now();

  if (!STRICT_FREE_DEMO_MODE) {
    throw new Error('STRICT_FREE_DEMO_MODE must be true.');
  }

  const baseMusicData = {
    title: 'Worship',
    prompt: 'Soulful',
    lyrics: 'Hallelujah',
    genre: 'Gospel',
    modelId: 'demo-full',
    taskId: 'shared-task-id-12345678901234567890',
  };

  try {
    await wrappedMusic({ ...baseMusicData, taskId: 'short', isDemo: false }, { auth: { uid } });
    throw new Error('Short taskId should have been rejected');
  } catch (e: any) {
    console.log('Short taskId rejected:', e.message);
  }

  const musicRes = await wrappedMusic({ ...baseMusicData, isDemo: false }, { auth: { uid } });
  if (!musicRes.isDemo || !musicRes.strictDemoMode) {
    throw new Error('Backend failed to enforce demo mode when client passed isDemo=false');
  }

  for (const language of ['English', 'Twi', 'Akan', 'Ga', 'Ewe']) {
    const res = await wrappedLyrics({ topic: 'Hope', language, isDemo: false }, { auth: { uid } });
    if (!res.isDemo || !res.strictDemoMode || !res.lyrics.includes(language)) {
      throw new Error(`Lyrics demo enforcement failed for language: ${language}`);
    }
  }

  console.log('STRICT FREE DEMO MODE SECURITY TESTS PASSED');
  testEnv.cleanup();
}

runSecurityTests().catch(console.error);
