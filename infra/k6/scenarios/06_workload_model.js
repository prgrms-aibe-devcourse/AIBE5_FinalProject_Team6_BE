/**
 * 통합 워크로드 모델 — 피드 60% · 대기열 20% · 주문 15% · 결제 5%
 * 목표: 단일 스크립트로 실제 서비스 트래픽 패턴 재현 및 혼합 부하 하 SLO 측정
 *
 * 사전 준비:
 *   - DB seed: fans.csv, orders.json (infra/k6/seed/ 참고)
 *   - Wiremock 기동 (결제 5% 구간): infra/k6/wiremock/ 참고
 *   - 실행:
 *     k6 run \
 *       -e FAN_POOL_SIZE=1000 \
 *       -e PRODUCT_ID=1 \
 *       -e ARTIST_ID=1 \
 *       -e ORDERS_JSON="$(cat seed/orders.json)" \
 *       --out experimental-prometheus-rw \
 *       scenarios/06_workload_model.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, localHeaders } from '../lib/auth.js';
import { waitForAccessToken } from '../lib/sse.js';
import { WRITE_THRESHOLDS } from '../lib/thresholds.js';

const PRODUCT_ID   = parseInt(__ENV.PRODUCT_ID   || '1');
const ARTIST_ID    = parseInt(__ENV.ARTIST_ID    || '1');
const FAN_POOL_SIZE = parseInt(__ENV.FAN_POOL_SIZE || '1000');
const QUEUE_FAN_ID = 1; // setup() 대기열 진입용 고정 (accessToken 공유)
const ORDERS = JSON.parse(__ENV.ORDERS_JSON || '[{"orderId":1,"amount":15000,"fanId":1}]');

// 워크로드 분포 확인용 카운터 (Grafana에서 분포 검증)
const wlFeed    = new Counter('wl_feed');
const wlQueue   = new Counter('wl_queue');
const wlOrder   = new Counter('wl_order');
const wlPayment = new Counter('wl_payment');

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

export function setup() {
  const joinRes = http.post(
    `${BASE_URL}/api/v1/queue/join/${PRODUCT_ID}`,
    null,
    { headers: { 'X-Fan-Id': String(QUEUE_FAN_ID) } },
  );
  if (joinRes.status !== 200 && joinRes.status !== 201) {
    throw new Error(`queue join failed: ${joinRes.status} ${joinRes.body}`);
  }

  const accessToken = waitForAccessToken(PRODUCT_ID, QUEUE_FAN_ID, 20000);
  if (!accessToken) {
    throw new Error(
      'accessToken 획득 실패 — 서버 큐 설정 확인:\n' +
      '  fandrops.queue.max-concurrent-processing=300\n' +
      '  fandrops.queue.advance-batch-size=300',
    );
  }
  console.log(`[setup] accessToken 획득 완료 (productId=${PRODUCT_ID}, fanPoolSize=${FAN_POOL_SIZE})`);
  return { accessToken };
}

export default function ({ accessToken }) {
  const fanId = ((__VU - 1) % FAN_POOL_SIZE) + 1;
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
    const res = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({ accessTicket: accessToken, items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
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
