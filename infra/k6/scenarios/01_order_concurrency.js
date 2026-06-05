/**
 * 주문 동시성 기준선 시나리오
 * 목표: 200 VU 동시 POST /orders → 재고 100개 → RESERVED 100건, oversell 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비:
 *   - DB seed: product id=1 (inventory.total_qty=100), fan id=1
 *   - 서버 설정: fandrops.queue.max-concurrent-processing=300
 *                fandrops.queue.advance-batch-size=300
 *                fandrops.queue.scheduler.interval-ms=1000
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/01_order_concurrency.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');
const FAN_ID = parseInt(__ENV.FAN_ID || '1');

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

export function setup() {
  // 1. 대기열 등록
  const joinRes = http.post(
    `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
    null,
    { headers: { 'X-Fan-Id': String(FAN_ID) } },
  );
  if (joinRes.status !== 200 && joinRes.status !== 201) {
    throw new Error(`queue join failed: ${joinRes.status} ${joinRes.body}`);
  }

  // 2. SSE 연결 → PROCESSING 전이 시 accessToken 수신 (스케줄러 최대 ~3tick 소요)
  const accessToken = waitForAccessToken(PRODUCT_ID, FAN_ID, 20000);
  if (!accessToken) {
    throw new Error(
      'accessToken 획득 실패. 서버 설정 확인:\n' +
      '  fandrops.queue.max-concurrent-processing=300\n' +
      '  fandrops.queue.advance-batch-size=300\n' +
      '  fandrops.queue.scheduler.interval-ms=1000',
    );
  }

  console.log(`[setup] accessToken 획득 완료 (fanId=${FAN_ID}, productId=${PRODUCT_ID})`);
  return { accessToken };
}

export default function ({ accessToken }) {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: accessToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: localHeaders(FAN_ID) },
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
