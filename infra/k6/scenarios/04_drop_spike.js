/**
 * 드롭스 스파이크 시나리오 — 0 → 1,000 VU 30초 급상승
 * 목표: ~1k TPS 주문 생성, 오버셀 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비: 01_order_concurrency.js와 동일한 서버 설정 필요
 *   - DB seed: product id=1 (inventory.total_qty=100 또는 스파이크 전 재설정)
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/04_drop_spike.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');
const FAN_ID = parseInt(__ENV.FAN_ID || '1');

const reservedCount = new Counter('spike_orders_reserved');

export const options = {
  scenarios: {
    drop_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: 1000, duration: '30s' },  // 급상승
        { target: 1000, duration: '30s' },  // 유지
        { target: 0, duration: '15s' },     // 종료
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    ...WRITE_THRESHOLDS,
    spike_orders_reserved: ['count<=100'],
  },
};

export function setup() {
  const joinRes = http.post(
    `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
    null,
    { headers: { 'X-Fan-Id': String(FAN_ID) } },
  );
  if (joinRes.status !== 200 && joinRes.status !== 201) {
    throw new Error(`queue join failed: ${joinRes.status} ${joinRes.body}`);
  }

  const accessToken = waitForAccessToken(PRODUCT_ID, FAN_ID, 20000);
  if (!accessToken) {
    throw new Error('accessToken 획득 실패 — 서버 queue 설정 확인');
  }
  return { accessToken };
}

export default function ({ accessToken }) {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: accessToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: localHeaders(FAN_ID) },
  );

  if (res.status === 201) reservedCount.add(1);
  check(res, { 'reserved or depleted': (r) => r.status === 201 || r.status === 409 });
}