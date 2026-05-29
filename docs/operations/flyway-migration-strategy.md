# Flyway 마이그레이션 전략

## 현재 상태 (Phase B — MVP 개발 중)

### 왜 이렇게 설정했는가

PR #63(order 도메인)에서 첫 번째 Flyway 마이그레이션 파일(V1)이 추가되면서 prod 기동 시 다음 오류가 발생했다.

```
FlywayException: Found non-empty schema(s) `fandrops` but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

**원인**: prod RDS `fandrops` 스키마는 이미 `ddl-auto: update`로 테이블이 생성된 상태였고, Flyway 이력 테이블(`flyway_schema_history`)은 존재하지 않았다. Flyway는 "내가 모르는 DB에 함부로 마이그레이션할 수 없다"고 판단해 기동을 거부했다.

**해결**: `baseline-on-migrate: true` + `baseline-version: 1` 설정으로 Flyway가 현재 DB를 V1 상태로 인식하고 이력 테이블을 생성하도록 했다.

### 현재 설정 (`application-prod.yml`)

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
  jpa:
    hibernate:
      ddl-auto: update
```

### 현재 방식의 동작

| 구성요소 | 역할 |
|---|---|
| `ddl-auto: update` | 엔티티 추가 시 Hibernate가 테이블 자동 생성/컬럼 추가 |
| `baseline-on-migrate: true` | Flyway 이력 테이블 없는 기존 DB에서도 크래시 없이 기동 |
| Flyway V1 | baseline으로 처리되어 실행 스킵, V2부터 적용 |

**주의**: `ddl-auto: update`와 Flyway가 동시에 활성화된 이중 관리 상태다. Flyway 마이그레이션 파일을 작성하지 않아도 Hibernate가 테이블을 자동 생성하므로, 실질적으로 Flyway는 이력 기록 기능만 하고 있다.

---

## Phase C 전환 — Flyway 단독 스키마 관리

### 언제 전환하는가

아래 조건이 **모두** 충족될 때 전환한다.

- [ ] MVP 핵심 기능 개발 완료 — 신규 엔티티 추가가 주 1회 미만으로 줄어든 시점
- [ ] 팀 전체가 "이후 모든 스키마 변경은 SQL 마이그레이션 파일로 작성" 에 합의
- [ ] 현재 prod DB 스키마 상태를 SQL로 덤프해 검증 완료

### 왜 지금은 전환하면 안 되는가

`ddl-auto: validate`로 전환하면 Hibernate가 엔티티와 DB 컬럼이 다를 때 기동을 거부한다. 팀원이 신규 엔티티를 추가하면서 마이그레이션 파일을 빠뜨리면 즉시 prod 크래시가 발생한다. 활발한 MVP 개발 단계에서는 이 리스크가 너무 크다.

### 전환 절차

**1단계 — 현재 prod DB를 마이그레이션 SQL로 확정**

```bash
# prod RDS에서 현재 스키마 덤프
mysqldump -h <RDS_HOST> -u fandrops_admin -p --no-data fandrops > V1__init_schema.sql
```

이 SQL이 `resources/db/migration/V1__init_schema.sql`로 이미 존재한다면 내용이 현재 DB와 일치하는지 검증한다.

**2단계 — `ddl-auto` 변경**

```yaml
# application-prod.yml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
  jpa:
    hibernate:
      ddl-auto: validate  # update → validate 변경
```

**3단계 — 팀 전체 작업 방식 변경**

이후 모든 스키마 변경(테이블 추가, 컬럼 추가/변경/삭제)은 반드시 마이그레이션 파일을 함께 작성한다.

```
resources/db/migration/
  V1__init_schema.sql         ← 초기 전체 스키마
  V2__add_orders_table.sql    ← PR #63 order 도메인
  V3__add_fan_column.sql      ← 예시
```

파일명 규칙: `V{버전}__{설명}.sql` (버전은 단조 증가, 한 번 커밋된 파일은 수정 금지)

**4단계 — 로컬 검증**

전환 전 로컬에서 `ddl-auto: validate` + Flyway로 기동 테스트를 반드시 수행한다.

### 전환 후 주의사항

- 마이그레이션 파일은 **한 번 push 후 절대 수정하지 않는다** — Flyway가 체크섬으로 변조를 감지해 기동 거부
- 동일 버전 번호 중복 사용 금지
- 컬럼 삭제 시 데이터 유실 위험 — 삭제 전 반드시 팀 리뷰