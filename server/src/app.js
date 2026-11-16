import { createServer } from 'node:http';
import { createHash, timingSafeEqual } from 'node:crypto';
import { ServiceError } from './providers.js';

export function validateChat(body) {
  if (!body || typeof body.message !== 'string' || !body.message.trim() || body.message.length > 1200 || !Array.isArray(body.history) || body.history.length > 6) throw new ServiceError(400, 'invalid_chat');
  if (body.history.some(t => !t || !['user','assistant'].includes(t.role) || typeof t.text !== 'string' || !t.text.trim() || t.text.length > 1200)) throw new ServiceError(400, 'invalid_history');
  return { message: body.message.trim(), history: body.history.map(t => ({ role: t.role, text: t.text })) };
}
export function validateCoordinates(body) {
  if (!body || !Number.isFinite(body.latitude) || !Number.isFinite(body.longitude) || Math.abs(body.latitude) > 90 || Math.abs(body.longitude) > 180) throw new ServiceError(400, 'invalid_coordinates');
  return body;
}
async function readJson(req) {
  if (!req.headers['content-type']?.startsWith('application/json')) throw new ServiceError(415, 'json_required');
  let size = 0; const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 32768) throw new ServiceError(413, 'body_too_large');
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ServiceError(400, 'invalid_json'); }
}
const hash = value => createHash('sha256').update(value).digest();
export function createApp(config, providers, now = Date.now) {
  if (typeof config.token !== 'string' || config.token.length < 32) throw new Error('ROADIE_ACCESS_TOKEN must contain at least 32 characters');
  const expected = hash(`Bearer ${config.token}`);
  const clients = new Map();
  let active = 0, minute = now(), accepted = 0;
  const server = createServer(async (req, res) => {
    const send = (status, body) => {
      if (res.destroyed || res.writableEnded) return;
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
      res.end(JSON.stringify(body));
    };
    const abort = new AbortController();
    res.on('close', () => { if (!res.writableEnded) abort.abort(); });
    const deadline = setTimeout(() => { abort.abort(); send(504, { error: 'timeout' }); }, 28_000);
    let counted = false;
    try {
      const url = new URL(req.url, 'http://localhost');
      if (req.method === 'GET' && url.pathname === '/health') { send(200, { status: 'ok', version: '0.1.0' }); return; }
      const ip = req.socket.remoteAddress ?? 'unknown';
      if (clients.size > 1024) for (const [key, value] of clients) if (now() - value.time >= 60000) clients.delete(key);
      if (!clients.has(ip) && clients.size >= 1024) throw new ServiceError(429, 'rate_limited');
      const bucket = clients.get(ip) ?? { time: now(), count: 0 };
      if (now() - bucket.time >= 60000) { bucket.time = now(); bucket.count = 0; }
      clients.set(ip, bucket);
      if (++bucket.count > 60) throw new ServiceError(429, 'rate_limited');
      if (!timingSafeEqual(hash(String(req.headers.authorization ?? '')), expected)) throw new ServiceError(401, 'unauthorized');
      if (now() - minute >= 60000) { minute = now(); accepted = 0; }
      if (++accepted > (config.rateLimit ?? 20) || active >= 2) throw new ServiceError(429, 'rate_limited');
      active++; counted = true;
      if (req.method === 'POST' && url.pathname === '/v1/chat') {
        const { message, history } = validateChat(await readJson(req));
        send(200, await providers.chat(message, history, abort.signal));
      } else if (req.method === 'GET' && url.pathname === '/v1/briefings') {
        const topic = url.searchParams.get('topic');
        if (!['tech', 'science', 'culture'].includes(topic)) throw new ServiceError(400, 'invalid_topic');
        send(200, await providers.briefing(topic, abort.signal));
      } else if (req.method === 'POST' && url.pathname === '/v1/rest-places') {
        const { latitude, longitude } = validateCoordinates(await readJson(req));
        send(200, await providers.places(latitude, longitude, abort.signal));
      } else send(404, { error: 'not_found' });
    } catch (error) {
      // Never log message bodies, coordinates, authorization headers, provider errors, or keys.
      send(error instanceof ServiceError ? error.status : 502, { error: error instanceof ServiceError ? error.code : 'upstream_unavailable' });
    } finally { clearTimeout(deadline); if (counted) active--; }
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  server.keepAliveTimeout = 5000;
  return server;
}
