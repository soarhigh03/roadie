import { createApp } from './app.js';
import { createProviders } from './providers.js';

const config = {
  token: process.env.ROADIE_ACCESS_TOKEN,
  openaiKey: process.env.OPENAI_API_KEY,
  model: process.env.OPENAI_MODEL || 'gpt-4.1-mini',
  kakaoKey: process.env.KAKAO_REST_API_KEY,
};
const port = Number(process.env.PORT || 8787);
const host = process.env.HOST || '127.0.0.1';
const server = createApp(config, createProviders(config));
server.listen(port, host, () => console.log(`Roadie server listening on ${host}:${port}`));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1), 5000).unref(); });
