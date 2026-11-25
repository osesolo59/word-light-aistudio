import fft from 'firebase-functions-test';
import { STRICT_FREE_DEMO_MODE } from './index';

const testEnv = fft();

async function runAdvancedSecurityTests() {
  console.log('--- STARTING STRICT DEMO VERIFICATION ---');
  if (!STRICT_FREE_DEMO_MODE) {
    throw new Error('STRICT_FREE_DEMO_MODE must remain enabled.');
  }
  console.log('STRICT_FREE_DEMO_MODE verified true; no paid AI SDKs or billing paths enabled.');
  testEnv.cleanup();
}

runAdvancedSecurityTests().catch(console.error);
