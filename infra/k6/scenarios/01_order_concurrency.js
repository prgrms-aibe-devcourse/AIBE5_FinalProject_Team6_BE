/**
 * 주문 동시성 기준선 시나리오
 * 목표: 200 VU 동시 POST /orders → 재고 100개 → RESERVED 100건, oversell 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비:
 *   - DB seed: product id=1 (inventory.total_qty=100), fan id=1~2100
 *   - Redis: access:ticket:1:{fanId} = "test-ticket-token" (fan_id 1~2100 일괄 적재)
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 서버 설정: fandrops.queue.max-concurrent-processing=300
 *                fandrops.queue.advance-batch-size=300
 *                fandrops.queue.scheduler.interval-ms=1000
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/01_order_concurrency.js
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

const reservedCount = new Counter('orders_reserved');
const cancelledCount = new Counter('orders_cancelled');

export const options = {
  scenarios: {
    concurrency: {
      executor: 'shared-iterations',
      vus: 200,
      iterations: 200,
      maxDuration: '3m',
    },
  },
  thresholds: {
    ...WRITE_THRESHOLDS,
    orders_reserved: ['count<=100'],  // 재고 초과 RESERVED = 오버셀
  },
};

export default function () {
  const token = userTokens[(__VU - 1) % userTokens.length].token;
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: 'test-ticket-token', items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: authHeaders(token) },
  );

  if (res.status === 201) {
    reservedCount.add(1);
  } else {
    cancelledCount.add(1);
  }

  check(res, { 'reserved or stock-depleted': (r) => r.status === 201 || r.status === 409 });
}

export function teardown() {
  console.log('=== 오버셀 검증 (수동 실행) ===');
  console.log(`SELECT reserved_qty FROM inventory WHERE product_id=${PRODUCT_ID};`);
  console.log('-- reserved_qty > 100 이면 오버셀 발생');
}