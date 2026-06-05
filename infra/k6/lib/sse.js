import http from 'k6/http';
import { BASE_URL } from './auth.js';

/**
 * SSE 스트림에 연결해 PROCESSING 이벤트의 accessToken을 반환한다.
 * 스케줄러 interval-ms=1000 권장. 기본 대기 15s.
 */
export function waitForAccessToken(productId, fanId, timeoutMs = 15000) {
  const res = http.get(
    `${BASE_URL}/api/v1/queue/stream/${productId}`,
    {
      headers: { Accept: 'text/event-stream', 'X-Fan-Id': String(fanId) },
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
