/**
 * 주문 동시성 기준선 시나리오
 * 목표: 200 VU 동시 POST /orders → 재고 100개 → RESERVED 100건, oversell 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비:
 *   - DB seed: product id=1 (inventory.total_qty=100), fan id 1~FAN_POOL_SIZE
 *   - 서버 설정: fandrops.queue.max-concurrent-processing=300  (≥ vus=200 이어야 전 VU 단일 배치)
 *                fandrops.queue.advance-batch-size=300
 *                fandrops.queue.scheduler.interval-ms=1000
 *   - 실행: k6 run -e FAN_POOL_SIZE=200 --out experimental-prometheus-rw scenarios/01_order_concurrency.js
 *
 * VU 흐름 (per-vu-iterations, iterations=2):
 *   iteration 1 — 대기열 진입(fanId별) + accessToken 획득
 *                 max-concurrent-processing ≥ vus 이면 전 VU가 단일 스케줄러 배치로 토큰 획득
 *   iteration 2 — POST /orders (200 VU 동시 발화 → 재고 100개 → oversell 검증)
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID   = parseInt(__ENV.PRODUCT_ID   || '1');
const FAN_POOL_SIZE = parseInt(__ENV.FAN_POOL_SIZE || '200');

const reservedCount  = new Counter('orders_reserved');
const cancelledCount = new Counter('orders_cancelled');

// VU별 accessToken — module-level 변수는 VU마다 독립된 메모리에 저장됨
let vuToken = null;

export const options = {
  scenarios: {
    concurrency: {
      executor: 'per-vu-iterations',
      vus: 200,
      iterations: 2,       // iteration 1: 토큰 획득, iteration 2: 주문
      maxDuration: '5m',
    },
  },
  thresholds: {
    ...WRITE_THRESHOLDS,
    orders_reserved: ['count<=100'],  // 재고 초과 RESERVED = 오버셀
  },
};

export default function () {
  const fanId = ((__VU - 1) % FAN_POOL_SIZE) + 1;

  // iteration 1: 대기열 진입 + 토큰 획득
  if (!vuToken) {
    const joinRes = http.post(
      `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
      null,
      { headers: { 'X-Fan-Id': String(fanId) } },
    );
    if (joinRes.status !== 200 && joinRes.status !== 201) {
      console.error(`[VU ${__VU} fan ${fanId}] queue join 실패: ${joinRes.status} ${joinRes.body}`);
      return;
    }

    vuToken = waitForAccessToken(PRODUCT_ID, fanId, 30000);
    if (!vuToken) {
      console.error(
        `[VU ${__VU} fan ${fanId}] accessToken 획득 실패. 서버 설정 확인:\n` +
        '  fandrops.queue.max-concurrent-processing=300\n' +
        '  fandrops.queue.advance-batch-size=300\n' +
        '  fandrops.queue.scheduler.interval-ms=1000',
      );
    }
    return; // iteration 1 종료 — iteration 2에서 주문 진행
  }

  // iteration 2: 주문 생성
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: vuToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: localHeaders(fanId) },
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
