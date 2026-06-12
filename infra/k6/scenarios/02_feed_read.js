/**
 * 피드 조회 Read P95 기준선
 * 목표: GET /artists/1/feeds P95 < 120ms
 * 파라미터: 정환철 확정 (2026-06-05)
 *
 * 사전 준비:
 *   - DB seed: artist_profile id=1, artist_feed artist_id=1 20개, user_follow fan_id=1 artist_id=1
 *   - tokens.csv: infra/k6/seed/tokens.csv (fan_id 1~2100 JWT)
 *   - 실행: k6 run --out experimental-prometheus-rw scenarios/02_feed_read.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';
import { BASE_URL, authHeaders } from '../lib/auth.js';
import { READ_THRESHOLDS } from '../lib/thresholds.js';

const ARTIST_ID = parseInt(__ENV.ARTIST_ID || '1');

const userTokens = new SharedArray('users', function () {
  return papaparse.parse(open('../seed/tokens.csv'), { header: true }).data;
});

export const options = {
  scenarios: {
    feed_read: {
      executor: 'constant-vus',
      vus: 50,
      duration: '2m',
    },
  },
  thresholds: READ_THRESHOLDS,
};

export default function () {
  const token = userTokens[(__VU - 1) % userTokens.length].token;
  const res = http.get(
    `${BASE_URL}/api/v1/artists/${ARTIST_ID}/feeds`,
    { headers: authHeaders(token) },
  );

  check(res, {
    'status 200': (r) => r.status === 200,
    'has items': (r) => {
      try { return Array.isArray(r.json('data.items')); } catch (_) { return false; }
    },
  });
}