// observability-metrics.md 기준 SLO
export const WRITE_THRESHOLDS = {
  http_req_duration: ['p(95)<300'],
  http_req_failed: ['rate<0.001'],
};

export const READ_THRESHOLDS = {
  http_req_duration: ['p(95)<120'],
  http_req_failed: ['rate<0.001'],
};

// 결제 SLO: 기획 300ms → 2,000ms 완화 (2026-06-22 팀 합의)
// 근거: Wiremock 즉시 응답 기준 앱 처리 P95 1,540ms — 외부 PG 레이턴시 추가 시 300ms 달성 구조적 불가
export const PAYMENT_THRESHOLDS = {
  http_req_duration: ['p(95)<2000'],
  http_req_failed: ['rate<0.001'],
};
