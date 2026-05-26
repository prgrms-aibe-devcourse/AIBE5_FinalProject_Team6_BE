# 로컬 모니터링 가이드

> Spring Boot → Actuator → Prometheus → Grafana 로컬 검증 가이드.
> AWS monitoring EC2 확장 체크리스트 포함.

---

## 디렉토리 구조

```
infra/
└── monitoring/
    ├── prometheus/
    │   └── prometheus.yml          # scrape 설정 (15s interval)
    └── grafana/
        └── provisioning/
            └── datasources/
                └── prometheus.yml  # Grafana datasource 자동 프로비저닝
compose.monitoring.yml              # Prometheus + Grafana (기본 compose.yaml과 분리)
```

---

## 사용 이미지 버전

| 서비스 | 이미지 |
|--------|--------|
| Prometheus | `prom/prometheus:v3.4.1` |
| Grafana | `grafana/grafana:12.0.1` |

팀 환경 통일을 위해 버전을 고정합니다. 업그레이드 시 이 파일과 `compose.monitoring.yml`을 함께 수정해 주세요.

---

## 전제조건

- Docker Desktop 실행 중 (Windows)
- Spring Boot가 `local` 프로파일로 포트 8080에서 실행 중

---

## 1. Spring Boot 로컬 실행

```bash
./gradlew :apps:api-server:bootRun
```

프로파일이 `local`로 설정되어 있어야 합니다 (`SPRING_PROFILES_ACTIVE=local`).

---

## 2. Actuator 엔드포인트 확인

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

`/actuator/prometheus` 응답에 `jvm_memory_used_bytes` 등 메트릭이 출력되면 정상입니다.

---

## 3. Prometheus + Grafana 실행

기본 개발 환경(MySQL/Redis)과 **분리**되어 있어 필요할 때만 실행합니다.

```bash
docker compose -f compose.yaml -f compose.monitoring.yml up -d
```

중단:

```bash
docker compose -f compose.yaml -f compose.monitoring.yml down
```

---

## 4. Prometheus target UP 확인

브라우저에서 접속: **http://localhost:9090/targets**

`fandrops-api-local` job이 **UP** 상태인지 확인합니다.

> `scrape_interval`을 15초로 설정한 이유: 기본값(1분)은 로컬 테스트에서 메트릭 반영이 너무 느립니다. 15초면 변경 직후 빠르게 확인할 수 있습니다.

---

## 5. Grafana datasource 확인

브라우저에서 접속: **http://localhost:3000**

- 기본 계정: `admin` / `admin`
- Connections → Data sources에서 `Prometheus`가 자동 등록되어 있는지 확인합니다.
- `Save & Test` 버튼으로 연결 상태를 검증합니다.

Grafana datasource URL: `http://prometheus:9090` (컨테이너 간 서비스 이름으로 통신)

> `grafana_data` named volume: Grafana 대시보드·설정을 컨테이너 재시작 후에도 유지하기 위해 사용합니다.

---

## 6. 최소 확인 PromQL

Grafana Explore 또는 Prometheus UI에서 아래 쿼리로 메트릭 수집을 확인합니다.

| 목적 | PromQL |
|------|--------|
| scrape target 상태 | `up` |
| HTTP 요청 수 | `http_server_requests_seconds_count` |
| JVM 메모리 사용량 | `jvm_memory_used_bytes` |
| DB 커넥션 풀 활성 수 | `hikaricp_connections_active` |
| CPU 사용률 | `process_cpu_usage` |

전체 수집 메트릭 목록: [`observability-metrics.md`](./observability-metrics.md)

---

## AWS 반영 시 체크리스트

### 새 JAR 수동 배포 후 확인

```bash
# EC2에서 (SSM Session Manager)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

### Nginx `/actuator` 접근 제한 (필수)

`/etc/nginx/default.d/fandrops-location.conf`에 아래 블록을 추가합니다.
`<monitoring-EC2-private-IP>`는 monitoring EC2의 프라이빗 IP로 교체하세요.

```nginx
location /actuator {
    allow <monitoring-EC2-private-IP>;
    deny all;

    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

> `/actuator/prometheus`는 전체 인터넷에 공개하지 않습니다. monitoring EC2에서만 접근 가능하게 제한합니다.

### monitoring EC2 구성 방향

- API 서버 EC2와 **별도** EC2에 Prometheus + Grafana 설치
- 운영 스케줄: **09:00~18:00 KST** (EventBridge Scheduler로 비용 절감)
- Grafana 3000 포트: **내 IP에서만 접근 허용** (SG 인바운드 규칙)
- Prometheus 9090 포트: **외부 비공개** (monitoring EC2 내부만 사용)
- API 서버 SG: monitoring EC2 프라이빗 IP → 8080 인바운드 허용 필요

---

## 미완료 작업 (TODO)

- [ ] monitoring EC2 생성 및 Prometheus/Grafana 설치
- [ ] monitoring EC2 SG 설정 (9090/3000 포트 제한)
- [ ] API 서버 SG에서 monitoring EC2 → 8080 허용 규칙 추가
- [ ] Nginx `/actuator` 접근 제한 적용 (위 설정 참고)
- [ ] Grafana 대시보드 JSON 프로비저닝
- [ ] GitHub Actions CD 파이프라인 (Phase 2)

> **Linux 환경 TODO:** Linux 로컬 개발자가 생길 경우 `compose.monitoring.yml`의 prometheus 서비스에 `extra_hosts: ["host.docker.internal:host-gateway"]`를 추가해야 합니다. 현재 팀은 Windows Docker Desktop 기준이므로 생략합니다.

> **CD 자동화:** GitHub Actions CD는 이번 작업 범위가 아니며 Phase 2로 남깁니다.