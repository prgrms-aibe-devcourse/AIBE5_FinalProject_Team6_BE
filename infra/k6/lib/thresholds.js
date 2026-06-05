// observability-metrics.md 기준 SLO
export const WRITE_THRESHOLDS = {
  http_req_duration: ['p(95)<300'],
  http_req_failed: ['rate<0.001'],
};

export const READ_THRESHOLDS = {
  http_req_duration: ['p(95)<120'],
  http_req_failed: ['rate<0.001'],
};

export const PAYMENT_THRESHOLDS = {
  http_req_duration: ['p(95)<3000'],
  http_req_failed: ['rate<0.01'],
};
