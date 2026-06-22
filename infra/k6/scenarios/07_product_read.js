/**
 * 상품 조회 처리량 기준선 — constant-arrival-rate 300 RPS
 * 목표: GET /products P95 < 120ms, error rate < 0.1%
 * 파라미터: 지영재 (2026-06-22)
 *
 * executor: constant-arrival-rate (RPS 직접 제어)
 *   - constant-vus는 응답 시간에 따라 실제 RPS가 가변됨
 *   - constant-arrival-rate는 목표 RPS를 보장하여 처리량 SLO 직접 검증 가능
 *   - preAllocatedVUs 50: p95=120ms 기준 VU당 ~8 req/s → 300 RPS에 37.5 VU 필요, 여유분 포함
 *   - maxVUs 200: 응답 지연 시 k6가 자동으로 VU 추가, 200 초과 시 dropped_iterations 발생
 *
 * 사전 준비:
 *   - DB: product id=1 (status=ON_SALE, drops_start_at=NULL) — EC2 RDS 확인 완료 (2026-06-22)
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 실행: BASE_URL=http://10.0.1.114:8081 k6 run --out experimental-prometheus-rw scenarios/07_product_read.js
 *
 * 주의: GET /api/v1/products 는 인증 필요 (ApiSecurityConfig permitAll 미등록)
 */
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';
import { READ_THRESHOLDS } from '../lib/thresholds.js';

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

export const options = {
  scenarios: {
    product_read: {
      executor: 'constant-arrival-rate',
      rate: 300,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: READ_THRESHOLDS,
};

export default function () {
  const token = userTokens[(__VU - 1) % userTokens.length].token;
  const res = http.get(
    `${BASE_URL}/api/v1/products?type=regular`,
    { headers: authHeaders(token) },
  );

  check(res, {
    'status 200': (r) => r.status === 200,
    'has items': (r) => {
      try { return Array.isArray(r.json('data.items')); } catch (_) { return false; }
    },
  });
}
