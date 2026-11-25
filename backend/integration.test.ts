import * as fft from 'firebase-functions-test';
import { generateMusic, generateLyrics, STRICT_FREE_DEMO_MODE } from './index';

const testEnv = fft();

describe('Strict Free Demo Mode Backend Logic', () => {
  const uid = 'test-user-123';

  afterAll(() => {
    testEnv.cleanup();
  });

  it('enforces STRICT_FREE_DEMO_MODE constant', () => {
    expect(STRICT_FREE_DEMO_MODE).toBe(true);
  });

  it('should fail if unauthenticated', async () => {
    const wrapped = testEnv.wrap(generateMusic);
    await expect(
      wrapped({ title: 'Test', taskId: 'valid-task-id-12345678901234', isDemo: false }, { auth: null })
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('should always return server-enforced demo audio even when client passes isDemo=false', async () => {
    const wrapped = testEnv.wrap(generateMusic);
    const res = await wrapped(
      {
        title: 'Test',
        taskId: 'valid-task-id-12345678901234',
        modelId: 'demo-full',
        genre: 'Gospel',
        prompt: 'Soulful',
        lyrics: 'Hallelujah',
        isDemo: false,
      },
      { auth: { uid } }
    );
    expect(res.isDemo).toBe(true);
    expect(res.strictDemoMode).toBe(true);
  });

  it('should return server-enforced demo lyrics for English, Twi, Akan, Ga, and Ewe', async () => {
    const wrapped = testEnv.wrap(generateLyrics);
    for (const language of ['English', 'Twi', 'Akan', 'Ga', 'Ewe']) {
      const res = await wrapped(
        { topic: 'Grace', genre: 'Worship', mood: 'Joyful', language, isDemo: false },
        { auth: { uid } }
      );
      expect(res.isDemo).toBe(true);
      expect(res.strictDemoMode).toBe(true);
      expect(res.lyrics).toContain(language);
    }
  });
});
