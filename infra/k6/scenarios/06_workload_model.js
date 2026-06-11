/**
 * 통합 워크로드 모델 — 피드 60% · 대기열 20% · 주문 15% · 결제 5%
 * 목표: 단일 스크립트로 실제 서비스 트래픽 패턴 재현 및 혼합 부하 하 SLO 측정
 *
 * 사전 준비:
 *   - DB seed: fans.csv, orders.json (infra/k6/seed/ 참고)
 *              fan id 1~FAN_POOL_SIZE
 *   - Wiremock 기동 (결제 5% 구간): infra/k6/wiremock/ 참고
 *   - 실행:
 *     k6 run \
 *       -e FAN_POOL_SIZE=1000 \
 *       -e PRODUCT_ID=1 \
 *       -e ARTIST_ID=1 \
 *       -e ORDERS_JSON="$(cat seed/orders.json)" \
 *       --out experimental-prometheus-rw \
 *       scenarios/06_workload_model.js
 *
 * VU 흐름: 첫 번째 iteration — 대기열 진입(fanId별) + accessToken 획득 (초기화 전용)
 *           이후 iteration — 60/20/15/5 분포 워크로드
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID    = parseInt(__ENV.PRODUCT_ID    || '1');
const ARTIST_ID     = parseInt(__ENV.ARTIST_ID     || '1');
const FAN_POOL_SIZE = parseInt(__ENV.FAN_POOL_SIZE || '1000');
const ORDERS = JSON.parse(__ENV.ORDERS_JSON || '[{"orderId":1,"amount":15000,"fanId":1}]');

// 워크로드 분포 확인용 카운터 (Grafana에서 분포 검증)
const wlFeed    = new Counter('wl_feed');
const wlQueue   = new Counter('wl_queue');
const wlOrder   = new Counter('wl_order');
const wlPayment = new Counter('wl_payment');

// VU별 accessToken — module-level 변수는 VU마다 독립된 메모리에 저장됨
let vuToken       = null;
let vuInitialized = false;

export const options = {
  scenarios: {
    workload: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: 50,  duration: '1m'  }, // 워밍업
        { target: 100, duration: '3m'  }, // 정상 부하
        { target: 150, duration: '1m'  }, // 스파이크
        { target: 0,   duration: '30s' }, // 쿨다운
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    ...WRITE_THRESHOLDS,       // P95 < 300ms, error rate < 0.1% (혼합 전체 기준)
    wl_feed:    ['count>0'],   // 각 구간에 실제 요청이 발생했는지 확인
    wl_queue:   ['count>0'],
    wl_order:   ['count>0'],
    wl_payment: ['count>0'],
  },
};

export default function () {
  const fanId = ((__VU - 1) % FAN_POOL_SIZE) + 1;

  // 첫 번째 iteration: 주문 15% 구간에 대비해 accessToken 미리 획득
  if (!vuInitialized) {
    vuInitialized = true;
    const joinRes = http.post(
      `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
      null,
      { headers: { 'X-Fan-Id': String(fanId) } },
    );
    if (joinRes.status === 200 || joinRes.status === 201) {
      vuToken = waitForAccessToken(PRODUCT_ID, fanId, 30000);
      if (!vuToken) {
        console.warn(`[VU ${__VU} fan ${fanId}] accessToken 획득 실패 — 주문 구간 건너뜀`);
      }
    } else {
      console.warn(`[VU ${__VU} fan ${fanId}] queue join 실패: ${joinRes.status} — 주문 구간 건너뜀`);
    }
    return; // 첫 번째 iteration은 초기화 전용
  }

  const r = Math.random();

  if (r < 0.60) {
    // ── 피드 조회 60% ──────────────────────────────────────────────────────
    wlFeed.add(1);
    const res = http.get(
      `${BASE_URL}/api/v1/artists/${ARTIST_ID}/feeds`,
      { headers: localHeaders(fanId) },
    );
    check(res, {
      '[feed] status 200': (r) => r.status === 200,
    });

  } else if (r < 0.80) {
    // ── 대기열 진입 20% (join만 호출, SSE 대기 없음 — 워크로드 분산 측정 목적) ──
    wlQueue.add(1);
    const res = http.post(
      `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
      null,
      { headers: { 'X-Fan-Id': String(fanId) } },
    );
    check(res, {
      '[queue] join accepted': (r) => r.status === 200 || r.status === 201 || r.status === 409,
    });

  } else if (r < 0.95) {
    // ── 주문 생성 15% ──────────────────────────────────────────────────────
    wlOrder.add(1);
    if (!vuToken) return; // 토큰 미획득 시 주문 건너뜀
    const res = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({ accessTicket: vuToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
      { headers: localHeaders(fanId) },
    );
    check(res, {
      '[order] reserved or depleted': (r) => r.status === 201 || r.status === 409,
    });

  } else {
    // ── 결제 확인 5% (Wiremock 필요) ───────────────────────────────────────
    wlPayment.add(1);
    const order = ORDERS[(__VU - 1) % ORDERS.length];
    const tossPaymentKey = `wl-${order.orderId}-${__ITER}`;
    const res = http.post(
      `${BASE_URL}/api/v1/payments/toss/confirm`,
      JSON.stringify({ tossPaymentKey, orderId: order.orderId, amount: order.amount }),
      { headers: localHeaders(order.fanId || fanId) },
    );
    check(res, {
      '[payment] confirm accepted': (r) => r.status === 200 || r.status === 201,
    });
  }
}