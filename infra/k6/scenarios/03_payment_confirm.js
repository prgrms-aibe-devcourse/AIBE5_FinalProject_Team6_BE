/**
 * 결제 확인 흐름 — Wiremock 기반 Toss PG 모킹
 * 목표: POST /payments/toss/confirm P95 < 2s, error rate < 0.1%
 * 파라미터: 장성재 확정 (2026-06-05) / executor 변경: 지영재 (2026-06-19)
 *
 * executor: shared-iterations (vus:50, iterations:500)
 *   - ramping-vus는 orderId가 VU당 재사용되어 2번째 iteration부터 전량 409 실패
 *   - shared-iterations + iterationInTest 인덱싱으로 iteration마다 고유 orderId 보장
 *
 * 사전 준비:
 *   1. Wiremock 기동 (infra/k6/wiremock/mappings/ stub 4종 자동 로드):
 *      docker run -d --name wiremock -p 8090:8080 \
 *        -v $(pwd)/infra/k6/wiremock/mappings:/home/wiremock/mappings \
 *        wiremock/wiremock:3.3.1 --global-response-templating
 *   2. TOSS_API_BASE_URL=http://localhost:8090 으로 앱 서버 재기동
 *   3. DB seed: RESERVED 주문 500건 (product_id=1, total_amount=15000)
 *   4. tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   5. 실행 — SCENARIO 선택:
 *      # 성공만 (기본)
 *      BASE_URL=http://10.0.1.114:8081 k6 run -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 *      # 특정 시나리오 단일 실행
 *      BASE_URL=http://10.0.1.114:8081 k6 run -e SCENARIO=timeout   -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *      BASE_URL=http://10.0.1.114:8081 k6 run -e SCENARIO=balance-error -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *      BASE_URL=http://10.0.1.114:8081 k6 run -e SCENARIO=server-error  -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 *      # 혼합 부하 (성공 70% / 타임아웃 10% / 잔액부족 10% / 서버오류 10%)
 *      BASE_URL=http://10.0.1.114:8081 k6 run -e SCENARIO=mixed -e ORDERS_JSON="$(cat seed/orders.json)" scenarios/03_payment_confirm.js
 *
 * SCENARIO → Wiremock stub 라우팅 (paymentKey prefix 기반):
 *   success      → toss-confirm-success.json      (200 즉시)
 *   timeout      → toss-confirm-timeout.json      (200, 5s 지연)
 *   balance-error → toss-confirm-balance-error.json (400 잔액부족)
 *   server-error  → toss-confirm-server-error.json  (500 PG 오류)
 *
 * orders.json 형식: [{"orderId":1,"amount":15000,"fanId":1,"orderPaymentKey":"seed-opk-000001"},...]
 */
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';
import { PAYMENT_THRESHOLDS } from '../lib/thresholds.js';
import { http5xxRate } from '../lib/metrics.js';

// pre-seeded RESERVED 주문 픽스처 (iterationInTest 기반 — iteration마다 고유 orderId 보장)
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
      executor: 'shared-iterations',
      vus: 50,
      iterations: 500,
      maxDuration: '5m',
    },
  },
  thresholds: PAYMENT_THRESHOLDS,
};

export default function () {
  const order = ORDERS[exec.scenario.iterationInTest % ORDERS.length];
  const fanId = order.fanId || exec.scenario.iterationInTest + 1;
  const token = userTokens[(fanId - 1) % userTokens.length].token;
  const prefix = resolvePrefix();
  // tossPaymentKey: Wiremock stub 라우팅 키 (prefix 기반, iteration마다 고유)
  // orderPaymentKey: DB에 저장된 실제 order_payment_key (주문 조회용)
  const tossPaymentKey = `${prefix}-${order.orderId}-${exec.scenario.iterationInTest}`;
  const orderPaymentKey = order.orderPaymentKey;

  const res = http.post(
    `${BASE_URL}/api/v1/payments/toss/confirm`,
    JSON.stringify({ tossPaymentKey, orderPaymentKey, orderId: order.orderId, amount: order.amount }),
    { headers: authHeaders(token) },
  );

  http5xxRate.add(res.status >= 500);

  // success/timeout: 200·201 기대 / balance-error: 400 / server-error: 5xx
  check(res, {
    'confirm accepted or expected error': (r) =>
      r.status === 200 || r.status === 201 || r.status === 400 || r.status === 500,
  });
}
