// Opt-in calls against real providers. Reads secrets from the ignored server/.env.
// node --env-file=server/.env scripts/verify-live.mjs --places --ai
import assert from 'node:assert/strict';
import { createApp } from '../server/src/app.js';
import { createProviders } from '../server/src/providers.js';

const ai = process.argv.includes('--ai'), places = process.argv.includes('--places');
if (!ai && !places) throw new Error('Choose --places and/or --ai to explicitly call a real provider.');
const config = { token: process.env.ROADIE_ACCESS_TOKEN, openaiKey: process.env.OPENAI_API_KEY,
  model: process.env.OPENAI_MODEL || 'gpt-4.1-mini', kakaoKey: process.env.KAKAO_REST_API_KEY };
if (ai && !config.openaiKey || places && !config.kakaoKey) throw new Error('Requested provider credentials are missing.');
const server = createApp(config, createProviders(config));
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const report = { at: new Date().toISOString(), testData: 'Fixed public test phrase and coordinates, not user history or device location' };
async function post(path, body) {
  const response = await fetch(`http://127.0.0.1:${server.address().port}${path}`, {
    method: 'POST', headers: { Authorization: `Bearer ${config.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(30000),
  });
  const json = await response.json();
  assert.equal(response.status, 200, `${path}: HTTP ${response.status}, ${json.error || 'unexpected response'}`);
  return json;
}
try {
  if (places) {
    const result = await post('/v1/rest-places', { latitude: 37.3, longitude: 127.1 });
    assert.equal(result.source, 'Kakao Local');
    assert.ok(result.places.length > 0 && result.places.length <= 3);
    for (const p of result.places) {
      assert.ok(p.id && p.name && p.category.includes('휴게소'));
      assert.ok(Number.isFinite(p.latitude) && Number.isFinite(p.longitude));
      assert.ok(p.distanceMeters >= 0 && p.distanceMeters <= 20000);
      assert.ok(!/택시|직원|영업소|사무소|관리소|전용/.test(p.name));
    }
    report.places = { status: 200, names: result.places.map(p => p.name) };
  }
  if (ai) {
    const result = await post('/v1/chat', { message: '안녕 로디. 오늘은 어떤 이야기를 해볼까?', history: [] });
    assert.equal(result.mode, 'live');
    assert.ok(typeof result.reply === 'string' && result.reply.trim().length > 0 && result.reply.length <= 600);
    assert.ok(!/[\u00b7\u2014]/u.test(result.reply));
    // Do not log response text; only validation metadata.
    report.ai = { status: 200, model: config.model, replyCharacters: result.reply.length };
  }
  console.log(JSON.stringify(report, null, 2));
} finally {
  server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
}
