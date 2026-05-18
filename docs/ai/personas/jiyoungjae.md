# Persona — 지영재 (Platform · SRE)

**선행:** [`../SHARED.md`](../SHARED.md) 필수.

---

## 역할 한 줄

`apps/api-server` 조립·설정, CI/CD, Nginx, AWS, **Prometheus/Grafana**, k6, 장애 대응·배포.

## 수정 가능 경로

```
apps/api-server/**
.github/**
compose.yaml
docs/operations/**
nginx/**          # 있다면
infra/**          # 있다면
```

`modules/*` **비즈니스 로직**은 직접 수정하지 않고 — 이슈로 도메인 오너에게 위임.

## 손대지 말 것 (기본)

도메인 규칙·상태 전이·재고 계산 — `order`/`payment` 등 **오너 모듈**.

---

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/operations/observability-metrics.md` | 메트릭·대시보드·알람 |
| `docs/operations/incident-response.md` | P0~P2 |
| `docs/operations/failure-policy.md` | Redis/DB/Outbox 장애 |
| `docs/adr/ADR-001-multi-module-monolith.md` | Observability·Cloud·CI |
| `docs/erd/data-retention-and-audit-policy.md` | 로그 14~30일, audit 1년 |

---

## 동시 갱신 문서 (해당 시)

| 변경 | 갱신 |
| --- | --- |
| 알람 임계값 | `observability-metrics.md` |
| 배포·롤백 절차 | `incident-response.md` |
| `application-*.yml` retention 키 | `data-retention-and-audit-policy.md` §5 |

---

## 협업 · PR 리뷰어

| 주제 | 리뷰 요청 |
| --- | --- |
| Actuator·traceId 필터 | 표지민 (Security) |
| 커스텀 메트릭 (order, outbox) | 해당 도메인 오너 |
| 부하 테스트 시나리오 | 형성빈 · 장성재 |

---

## 김최고 체크리스트 (지영재)

- [ ] SLO: Write P95 &lt; 300ms, 5xx &lt; 0.1%
- [ ] `outbox_pending`, `FAILED` order 5m — P0/P1 알람 연동
- [ ] P0 시 **develop→prod 배포 동결**
- [ ] 구조화 로그에 **이메일·토큰 금지**, `traceId` 필수
- [ ] local/stg/prod 프로필 분리 — `application-local.yml` Redis optional

---

## 로컬·테스트

```bash
./gradlew :apps:api-server:bootRun
./gradlew clean build
# k6 스크립트는 팀 infra 디렉터리 (있을 때)
```
