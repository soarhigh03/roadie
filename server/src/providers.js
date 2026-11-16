import { XMLParser } from 'fast-xml-parser';
import { createHash } from 'node:crypto';

export class ServiceError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
export const clean = value => String(value ?? '').replace(/<[^>]*>/g, '').replace(/[\u00b7\u2014]/g, ',').replace(/\s+/g, ' ').trim();
const feeds = {
  tech: { url: 'https://www.hankyung.com/feed/it', source: '한국경제', host: 'hankyung.com' },
  science: { url: 'https://www.nasa.gov/feed/', source: 'NASA', host: 'nasa.gov' },
  culture: { url: 'https://www.hankyung.com/feed/life', source: '한국경제', host: 'hankyung.com' },
};
const safeUrl = (value, host) => {
  try { const u = new URL(value); return u.protocol === 'https:' && !u.username && !u.password && (u.hostname === host || u.hostname.endsWith(`.${host}`)); }
  catch { return false; }
};

export function parseFeed(xml, topic, now = Date.now()) {
  if (!feeds[topic] || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw new ServiceError(502, 'invalid_feed');
  const parsed = new XMLParser({ ignoreAttributes: false, processEntities: false }).parse(xml);
  const raw = parsed?.rss?.channel?.item;
  const items = !raw ? [] : Array.isArray(raw) ? raw : [raw];
  const seen = new Set();
  return items.flatMap(item => {
    const url = typeof item.link === 'string' ? item.link : '';
    const date = Date.parse(item.pubDate);
    const title = clean(item.title).slice(0, 200);
    if (!safeUrl(url, feeds[topic].host) || !title || !Number.isFinite(date) || date > now + 300_000 || seen.has(url)) return [];
    seen.add(url);
    // A short publisher headline is an extract, never represented as an AI-written article.
    return [{ id: createHash('sha256').update(url).digest('hex').slice(0, 20), title,
      summary: title, summaryKind: 'headline', source: feeds[topic].source, url,
      publishedAt: new Date(date).toISOString(), stale: now - date > 24 * 60 * 60 * 1000 }];
  }).sort((a, b) => b.publishedAt.localeCompare(a.publishedAt)).slice(0, 3);
}

async function boundedText(response, limit) {
  if (!response.ok) throw new ServiceError(502, 'provider_error');
  let bytes = 0; const chunks = [];
  for await (const chunk of response.body) {
    bytes += chunk.length;
    if (bytes > limit) { await response.body.cancel().catch(() => {}); throw new ServiceError(502, 'provider_response_too_large'); }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

export function createProviders(config, fetchImpl = fetch) {
  const cache = new Map();
  async function call(url, options, signal, limit = 131072) {
    const response = await fetchImpl(url, { ...options, redirect: 'error', signal: AbortSignal.any([signal, AbortSignal.timeout(20_000)]) });
    return boundedText(response, limit);
  }
  return {
    async chat(message, history, signal) {
      if (!config.openaiKey) throw new ServiceError(503, 'ai_not_configured');
      const instructions = '당신은 로디, 혼자 운전하는 사람의 다정한 동행입니다. 한국어로 최대 3문장, 240자 안에 응답하세요. 운전에 집중할 시간을 존중하세요. 졸음이나 피로 표현에는 안전한 정차와 휴식을 제안하세요. 대화로 졸음을 해결한다고 말하지 마세요. 확인하지 않은 실시간 뉴스, 차량 상태, 위치, 날씨를 사실처럼 말하지 마세요. 도구를 실행하거나 경로를 설정했다고 주장하지 마세요. 중앙점과 em dash를 사용하지 마세요.';
      const result = JSON.parse(await call('https://api.openai.com/v1/responses', {
        method: 'POST', headers: { 'Authorization': `Bearer ${config.openaiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: config.model, store: false, instructions, max_output_tokens: 300,
          input: [...history.map(t => ({ role: t.role, content: t.text })), { role: 'user', content: message }] }),
      }, signal));
      const text = clean((result.output ?? []).filter(o => o.type === 'message').flatMap(o => o.content ?? []).filter(c => c.type === 'output_text').map(c => c.text).join(' '));
      if (!text || result.status === 'incomplete') throw new ServiceError(502, 'empty_ai_response');
      return { reply: text.slice(0, 600), mode: 'live' };
    },
    async briefing(topic, signal) {
      const cached = cache.get(topic);
      if (cached && Date.now() - cached.time < 600_000) return { ...cached.value, articles: cached.value.articles.map(a => ({ ...a, stale: Date.now() - Date.parse(a.publishedAt) > 86400000 })) };
      const xml = await call(feeds[topic].url, { headers: { 'User-Agent': 'Roadie/0.1 (+https://github.com/soarhigh03/roadie)', Accept: 'application/rss+xml, application/xml, text/xml' } }, signal, 2_000_000);
      const value = { topic, fetchedAt: new Date().toISOString(), articles: parseFeed(xml, topic), mode: 'live' };
      cache.set(topic, { time: Date.now(), value });
      return value;
    },
    async places(latitude, longitude, signal) {
      if (!config.kakaoKey) throw new ServiceError(503, 'places_not_configured');
      const url = new URL('https://dapi.kakao.com/v2/local/search/keyword.json');
      Object.entries({ query: '휴게소', x: longitude, y: latitude, radius: 20000, sort: 'distance', size: 3 }).forEach(([k,v]) => url.searchParams.set(k,String(v)));
      const json = JSON.parse(await call(url, { headers: { Authorization: `KakaoAK ${config.kakaoKey}` } }, signal));
      const places = (json.documents ?? []).filter(p => Number.isFinite(Number(p.y)) && Number.isFinite(Number(p.x))).slice(0,3).map(p => ({
        id: String(p.id), name: clean(p.place_name), address: clean(p.road_address_name || p.address_name),
        latitude: Number(p.y), longitude: Number(p.x), distanceMeters: Number(p.distance) || 0,
      }));
      return { places, source: 'Kakao Local', fetchedAt: new Date().toISOString() };
    },
  };
}
