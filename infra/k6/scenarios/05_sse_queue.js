/**
 * SSE 대기열 연결 안정성 시나리오
 * 목표: Nginx worker_connections · JVM FD 한계 검증
 *
 * v2 구조 (2단계):
 *   capacity_fill  — 2000 VU로 SSE 슬롯을 채우고 유지 (T+0 ~ T+3m30s)
 *   overflow_probe — 슬롯이 꽉 찬 상태에서 추가 연결 시도 → 429+retryable:true 계약 검증 (T+2m ~ T+2m30s)
 *
 * 변경 이유:
 *   기존 ramping-vus 3단계(normal→boundary→overflow)는 SSE long-lived 연결을 stage 종료 시
 *   k6가 interrupt하여 서버가 200을 줬어도 metric에 반영되지 않는 구조적 문제가 있었음.
 *   capacity_fill로 슬롯을 채운 뒤 별도 constant-arrival-rate로 초과 검증을 분리.
 *
 * 파라미터: 장성재 확정 (2026-06-05) / 시나리오 재설계 지영재 (2026-06-24)
 *
 * 사전 준비:
 *   - Nginx: worker_connections ≥ 4096 확인 (지영재)
 *   - JVM: ulimit -n ≥ 65535 확인 (지영재)
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 실행: k6 run -e BASE_URL=... -e PRODUCT_ID=4 scenarios/05_sse_queue.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL } from '../lib/auth.js';

const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

const connectionAccepted = new Counter('sse_connections_accepted');
const connectionRejected = new Counter('sse_connections_rejected');

export const options = {
  scenarios: {
    // 단계 1: 2000 슬롯 채우기 — SSE 연결이 서버 상한까지 정상 유지되는지 검증
    capacity_fill: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 2000 },  // 점진 증가 (서버 부하 완화)
        { duration: '2m',  target: 2000 },  // overflow_probe 기간 동안 슬롯 유지
        { duration: '30s', target: 0 },
      ],
      // SSE 연결은 310s 동안 blocking → stage 종료 후 최소 대기만 주고 interrupt
      gracefulRampDown: '10s',
    },
    // 단계 2: 슬롯 초과 검증 — capacity_fill이 2000 VU에 도달한 뒤 시작
    overflow_probe: {
      executor: 'constant-arrival-rate',
      startTime: '2m',
      duration: '30s',
      rate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds: {
    // capacity_fill: TCP 연결 오류 없어야 함 (interrupt된 요청은 0/0으로 집계됨)
    'http_req_failed{scenario:capacity_fill}': ['rate<0.001'],
    // overflow_probe: 429 응답에 retryable:true 포함 여부 (99% 이상)
    'checks{scenario:overflow_probe}': ['rate>0.99'],
    // 초과 구간에서 반드시 용량 거부가 발생해야 함
    sse_connections_rejected: ['count>0'],
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
