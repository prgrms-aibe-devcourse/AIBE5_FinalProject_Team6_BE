import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200) {
    throw new Error(`login failed: status=${res.status} body=${res.body}`);
  }
  return res.json('data.accessToken');
}

export function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

// local 프로파일 전용: JWT 없이 X-Fan-Id 헤더로 인증 우회
export function localHeaders(fanId) {
  return { 'Content-Type': 'application/json', 'X-Fan-Id': String(fanId) };
}
