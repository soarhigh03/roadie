// Run from the repository root with node --env-file=server/.env scripts/smoke-server.mjs.
import assert from 'node:assert/strict';
const base = `http://127.0.0.1:${process.env.PORT || 8787}`;
const headers = { Authorization: `Bearer ${process.env.ROADIE_ACCESS_TOKEN}`, 'Content-Type': 'application/json' };
const call = (path, options = {}) => fetch(base + path, { signal: AbortSignal.timeout(35000), ...options });
assert.equal((await call('/health')).status, 200);
assert.equal((await call('/v1/briefings?topic=tech')).status, 401);
for (const topic of ['tech', 'science', 'culture']) {
  const response = await call(`/v1/briefings?topic=${topic}`, { headers });
  assert.equal(response.status, 200, `RSS ${topic} HTTP status`);
  const result = await response.json();
  assert.equal(result.mode, 'live');
  assert.ok(result.articles.length > 0, `No dated RSS items in ${topic}`);
  for (const a of result.articles) {
    assert.ok(a.url.startsWith('https://'));
    assert.ok(Number.isFinite(Date.parse(a.publishedAt)));
    assert.equal(a.summaryKind, 'headline');
  }
  console.log(`${topic}: ${result.articles.length} real RSS items, source=${result.articles[0].source}, published=${result.articles[0].publishedAt}`);
}
if (!process.env.OPENAI_API_KEY) {
  const response = await call('/v1/chat', { method: 'POST', headers, body: JSON.stringify({ message: '연결 확인', history: [] }) });
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, 'ai_not_configured');
  console.log('AI credentials absent: explicit 503 verified, no synthetic fallback.');
}
console.log('Local HTTP smoke checks passed.');
