/**
 * 결제 확인 흐름 — Wiremock 기반 Toss PG 모킹
 * 목표: POST /payments/toss/confirm P95 < 3s, error rate < 1%
 * 파라미터: 장성재 확정 (2026-06-05)
 *
 * 사전 준비:
 *   1. Wiremock 기동 (infra/k6/wiremock/mappings/ stub 4종 자동 로드):
 *      docker run -d --name wiremock -p 8090:8080 \
 *        -v $(pwd)/infra/k6/wiremock/mappings:/home/wiremock/mappings \
 *        wiremock/wiremock:3.3.1 --global-response-templating
 *   2. TOSS_API_BASE_URL=http://localhost:8090 으로 앱 서버 재기동
 *   3. DB seed: orders id=1..50 (status=RESERVED, product_id=1, total_amount=15000, fan_id=1..50)
 *   4. tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   5. 실행 — SCENARIO 선택:
 *      # 성공만 (기본)
 *      k6 run -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 *      # 특정 시나리오 단일 실행
 *      k6 run -e SCENARIO=timeout   -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *      k6 run -e SCENARIO=balance-error -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *      k6 run -e SCENARIO=server-error  -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 *      # 혼합 부하 (성공 70% / 타임아웃 10% / 잔액부족 10% / 서버오류 10%)
 *      k6 run -e SCENARIO=mixed -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 * SCENARIO → Wiremock stub 라우팅 (paymentKey prefix 기반):
 *   success      → toss-confirm-success.json      (200 즉시)
 *   timeout      → toss-confirm-timeout.json      (200, 5s 지연)
 *   balance-error → toss-confirm-balance-error.json (400 잔액부족)
 *   server-error  → toss-confirm-server-error.json  (500 PG 오류)
 *
 * orders.json 형식: [{"orderId":1,"amount":15000,"fanId":1},...]
 */
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';
import { PAYMENT_THRESHOLDS } from '../lib/thresholds.js';

// pre-seeded RESERVED 주문 픽스처 (VU별 orderId 중복 없이 분배)
const ORDERS = JSON.parse(__ENV.ORDERS_JSON || '[{"orderId":1,"amount":15000,"fanId":1}]');

// SCENARIO: success | timeout | balance-error | server-error | mixed
const SCENARIO = __ENV.SCENARIO || 'success';

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

function resolvePrefix() {
  if (SCENARIO === 'mixed') {
    const r = Math.random();
    if (r < 0.70) return 'success';
    if (r < 0.80) return 'timeout';
    if (r < 0.90) return 'balance';
    return 'error';
  }
  const map = { success: 'success', timeout: 'timeout', 'balance-error': 'balance', 'server-error': 'error' };
  return map[SCENARIO] || 'success';
}

export const options = {
  scenarios: {
    payment_confirm: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: 50, duration: '30s' },
        { target: 50, duration: '2m' },
        { target: 0, duration: '10s' },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: PAYMENT_THRESHOLDS,
};

export default function () {
  const order = ORDERS[(__VU - 1) % ORDERS.length];
  const fanId = order.fanId || __VU;
  const token = userTokens[(fanId - 1) % userTokens.length].token;
  const prefix = resolvePrefix();
  // prefix가 Wiremock stub 라우팅 키 — 멱등키 충돌 방지를 위해 __ITER 포함
  const tossPaymentKey = `${prefix}-${order.orderId}-${__ITER}`;

  const res = http.post(
    `${BASE_URL}/api/v1/payments/toss/confirm`,
    JSON.stringify({ tossPaymentKey, orderId: order.orderId, amount: order.amount }),
    { headers: authHeaders(token) },
  );

  // success/timeout: 200·201 기대 / balance-error: 400 / server-error: 5xx
  check(res, {
    'confirm accepted or expected error': (r) =>
      r.status === 200 || r.status === 201 || r.status === 400 || r.status === 500,
  });
}
