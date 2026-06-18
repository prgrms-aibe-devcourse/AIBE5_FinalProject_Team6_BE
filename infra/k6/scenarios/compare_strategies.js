/**
 * 재고 전략 비교 — 로컬 환경용
 * 실행:
 *   전략 A: k6 run scenarios/compare_strategies.js
 *   전략 B: override.yml에서 lock-strategy: redisson 으로 변경 후 재실행
 *
 * 사전 조건:
 *   - 앱 실행 중 (local 프로필, H2)
 *   - product_id=1 존재 (시드 데이터)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = parseInt(__ENV.PRODUCT_ID || '1');
const VUS = parseInt(__ENV.VUS || '50');

const reservedCount  = new Counter('orders_reserved');
const conflictRate   = new Rate('orders_conflict');

export const options = {
  scenarios: {
    spike: {
      executor: 'constant-vus',
      vus: VUS,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  // 재고 5000으로 증가 (시드 100 + 4900 추가)
  const res = http.post(
    `${BASE_URL}/api/v1/products/${PRODUCT_ID}/restock`,
    JSON.stringify({ quantity: 4900 }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200) {
    console.error(`restock 실패: ${res.status} ${res.body}`);
  } else {
    const body = res.json();
    console.log(`재고 설정 완료: totalQty=${body.data.totalQty}`);
  }
}

export default function () {
  const fanId = (__VU % 100) + 1;  // 1~100 순환
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ accessTicket: null, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    { headers: { 'Content-Type': 'application/json', 'X-Fan-Id': String(fanId) } },
  );

  const reserved = res.status === 201 || res.status === 200;
  const conflict  = res.status === 409;

  if (reserved) reservedCount.add(1);
  conflictRate.add(conflict ? 1 : 0);

  check(res, {
    'reserved or stock-conflict': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });
}
