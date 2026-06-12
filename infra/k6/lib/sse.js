import http from 'k6/http';
import { BASE_URL } from './auth.js';

/**
 * SSE 스트림에 연결해 PROCESSING 이벤트의 accessToken을 반환한다.
 * Bearer token 방식 — prod 프로파일 대응.
 */
export function waitForAccessToken(productId, token, timeoutMs = 15000) {
  const res = http.get(
    `${BASE_URL}/api/v1/queue/stream/${productId}`,
    {
      headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
      timeout: `${timeoutMs}ms`,
    },
  );

  const body = (res.body || '').toString();
  for (const block of body.split('\n\n')) {
    const dataLine = block.split('\n').find((l) => l.startsWith('data:'));
    if (!dataLine) continue;
    try {
      const event = JSON.parse(dataLine.replace(/^data:\s*/, ''));
      if (event.status === 'PROCESSING' && event.accessToken) {
        return event.accessToken;
      }
    } catch (_) { /* ignore */ }
  }
  return null;
}