import { Rate } from 'k6/metrics';

// 5xx(서버 오류)만 별도 추적 — http_req_failed(4xx+5xx 합산)와 분리
// success 시나리오 기준 SLO 적용. mixed/error 시나리오는 분석용으로 임계값 실패가 예상 동작
export const http5xxRate = new Rate('http_5xx_rate');
