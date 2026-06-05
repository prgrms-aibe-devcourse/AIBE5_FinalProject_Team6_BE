/**
 * 결제 확인 흐름 — Wiremock 기반 Toss PG 모킹
 * 목표: POST /payments/toss/confirm P95 < 3s, error rate < 1%
 * 파라미터: 장성재 확정 (2026-06-05)
 *
 * 사전 준비:
 *   1. compose.yaml에 Wiremock 서비스 추가 (infra/k6/wiremock/ 참고)
 *   2. TOSS_API_BASE_URL=http://wiremock:8080 으로 앱 서버 재기동
 *   3. DB seed: orders id=1..50 (status=RESERVED, product_id=1, total_amount=15000, fan_id=1..50)
 *   4. 실행:
 *      k6 run \
 *        -e ORDERS_JSON="$(cat seed/orders.json)" \
 *        --out experimental-prometheus-rw \
 *        scenarios/03_payment_confirm.js
 *
 * orders.json 형식: [{"orderId":1,"amount":15000},{"orderId":2,"amount":15000},...]
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { PAYMENT_THRESHOLDS } from '../lib/thresholds.js';

// pre-seeded RESERVED 주문 픽스처 (VU별 orderId 중복 없이 분배)
const ORDERS = JSON.parse(__ENV.ORDERS_JSON || '[{"orderId":1,"amount":15000}]');

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
  // VU 인덱스 기반으로 주문 픽스처 선택 (0-based)
  const order = ORDERS[(__VU - 1) % ORDERS.length];
  // 각 VU마다 고유 tossPaymentKey 생성 (멱등키 충돌 방지)
  const tossPaymentKey = `load-test-${order.orderId}-${__ITER}`;
  const fanId = order.fanId || __VU;

  const res = http.post(
    `${BASE_URL}/api/v1/payments/toss/confirm`,
    JSON.stringify({ tossPaymentKey, orderId: order.orderId, amount: order.amount }),
    { headers: localHeaders(fanId) },
  );

  check(res, {
    'confirm accepted': (r) => r.status === 200 || r.status === 201,
  });
}
