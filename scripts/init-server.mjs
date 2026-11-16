import { randomBytes } from 'node:crypto';
import { writeFileSync, existsSync } from 'node:fs';
const target = new URL('../server/.env', import.meta.url);
if (existsSync(target)) {
  console.log('Existing server/.env preserved.');
} else {
  writeFileSync(target, `HOST=127.0.0.1\nPORT=8787\nROADIE_ACCESS_TOKEN=${randomBytes(32).toString('hex')}\nOPENAI_API_KEY=\nOPENAI_MODEL=gpt-4.1-mini\nKAKAO_REST_API_KEY=\n`, { mode: 0o600, flag: 'wx' });
  console.log('Created ignored server/.env. Read ROADIE_ACCESS_TOKEN locally and enter it in the parked app settings.');
}
