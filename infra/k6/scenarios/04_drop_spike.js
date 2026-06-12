/**
 * 드롭스 스파이크 시나리오 — 0 → 1,000 VU 30초 급상승
 * 목표: ~1k TPS 주문 생성, 오버셀 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비: 01_order_concurrency.js와 동일한 서버 설정 필요
 *   - DB seed: product id=1 (inventory.total_qty=100 또는 스파이크 전 재설정)
 *   - Redis: access:ticket:1:{fanId} = "test-ticket-token" (fan_id 1~2100 일괄 적재)
 *     ※ 시나리오 01 실행 후 invalidate된 티켓이 있으므로 재적재 필요
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/04_drop_spike.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

const reservedCount = new Counter('spike_orders_reserved');

export const options = {
  scenarios: {
    drop_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: 1000, duration: '30s' },  // 급상승
        { target: 1000, duration: '30s' },  // 유지
        { target: 0,    duration: '15s' },  // 종료
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    ...WRITE_THRESHOLDS,
    spike_orders_reserved: ['count<=100'],
  },
};

export default function () {
  const token = userTokens[(__VU - 1) % userTokens.length].token;
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: 'test-ticket-token', items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: authHeaders(token) },
  );

  if (res.status === 201) reservedCount.add(1);
  check(res, { 'reserved or depleted': (r) => r.status === 201 || r.status === 409 });
}