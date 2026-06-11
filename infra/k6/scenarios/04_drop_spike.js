/**
 * 드롭스 스파이크 시나리오 — 0 → 1,000 VU 30초 급상승
 * 목표: ~1k TPS 주문 생성, 오버셀 0건
 * 파라미터: 형성빈 확정 (2026-06-05)
 *
 * 사전 준비: 01_order_concurrency.js와 동일한 서버 설정 필요
 *   - DB seed: product id=1 (inventory.total_qty=100 또는 스파이크 전 재설정)
 *              fan id 1~FAN_POOL_SIZE
 *   - 실행: k6 run -e FAN_POOL_SIZE=1000 --out experimental-prometheus-rw scenarios/04_drop_spike.js
 *
 * VU 흐름: 각 VU 첫 번째 iteration — 대기열 진입(fanId별) + accessToken 획득
 *           이후 iteration — POST /orders (재고 소진까지 반복)
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID    = parseInt(__ENV.PRODUCT_ID    || '1');
const FAN_POOL_SIZE = parseInt(__ENV.FAN_POOL_SIZE || '1000');

const reservedCount = new Counter('spike_orders_reserved');

// VU별 accessToken — module-level 변수는 VU마다 독립된 메모리에 저장됨
let vuToken = null;

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
  const fanId = ((__VU - 1) % FAN_POOL_SIZE) + 1;

  // 첫 번째 iteration: 대기열 진입 + 토큰 획득
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
      console.error(`[VU ${__VU} fan ${fanId}] accessToken 획득 실패 — 서버 queue 설정 확인`);
    }
    return; // 첫 번째 iteration 종료
  }

  // 이후 iteration: 주문 생성
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: vuToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: localHeaders(fanId) },
  );

  if (res.status === 201) reservedCount.add(1);
  check(res, { 'reserved or depleted': (r) => r.status === 201 || r.status === 409 });
}
