/**
 * SSE 대기열 연결 안정성 시나리오
 * 목표: Nginx worker_connections · JVM FD 한계 검증
 * 단계: 1,000 VU(정상) → 1,800 VU(경계) → 2,100 VU(429 계약 검증)
 * 파라미터: 장성재 확정 (2026-06-05)
 *
 * 사전 준비:
 *   - Nginx: worker_connections ≥ 2048 확인 (지영재)
 *   - JVM: ulimit -n ≥ 8192 확인 (지영재)
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/05_sse_queue.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';

const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

const connectionAccepted = new Counter('sse_connections_accepted');
const connectionRejected = new Counter('sse_connections_rejected');

export const options = {
  scenarios: {
    // 단계 1: 정상 부하 — P95 연결 지연·메모리 기준선
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: 1000, duration: '30s' },
        { target: 1000, duration: '1m' },
        { target: 0,    duration: '15s' },
      ],
      gracefulRampDown: '15s',
    },
    // 단계 2: 경계 — 상한 직전 안정성
    boundary: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '2m',
      stages: [
        { target: 1800, duration: '30s' },
        { target: 1800, duration: '1m' },
        { target: 0,    duration: '15s' },
      ],
      gracefulRampDown: '15s',
    },
    // 단계 3: 초과 — 429 + retryable:true 계약 검증
    overflow: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '4m30s',
      stages: [
        { target: 2100, duration: '30s' },
        { target: 2100, duration: '30s' },
        { target: 0,    duration: '15s' },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 단계 1·2: 연결 거부 없어야 함
    'http_req_failed{scenario:normal_load}': ['rate<0.001'],
    'http_req_failed{scenario:boundary}':    ['rate<0.01'],
    // 단계 3: 429 비율 검증 (초과 구간은 일부 거부 정상)
    sse_connections_rejected: ['count>0'],  // 초과 구간에서 반드시 거부 발생해야 함
  },
};

export default function () {
  const token = userTokens[(__VU - 1) % userTokens.length].token;
  const res = http.get(
    `${BASE_URL}/api/v1/queue/stream/${PRODUCT_ID}`,
    {
      headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
      timeout: '310s',
    },
  );

  if (res.status === 200) {
    connectionAccepted.add(1);
  } else if (res.status === 429) {
    connectionRejected.add(1);
    check(res, {
      '429 has retryable:true': (r) => {
        try { return r.json('error.retryable') === true; } catch (_) { return false; }
      },
    });
  } else {
    check(res, { 'unexpected status': () => false });
  }
}