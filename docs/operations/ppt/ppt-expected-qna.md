# FANDROPS 발표 예상 질문 · 답변집

> 기준 문서: `docs/operations/ppt/ppt-presentation-final.md`  
> 보조 문서: `docs/operations/aws/current-infra-state.md`, `docs/operations/aws/nginx-bluegreen-strategy.md`, `docs/operations/observability-metrics.md`, `docs/operations/failure-policy.md`, `docs/operations/k6/*.md`  
> 목적: AWS / 인프라 / 배포 / 모니터링 / k6 부하 테스트 발표 Q&A 대응  
> 작성일: 2026-06-25

---

## 1. 발표 핵심 방어 논리 요약

### 1-1. 한 문장 요약

> "월 72,000원 예산과 5주 개발 기간 제약 안에서, 실제 AWS 리소스를 구성하고 7개 부하 시나리오로 오버셀 0·중복 주문 0·결제/재고 정합성을 수치로 검증했습니다."

### 1-2. 우리가 검증한 것

| 항목 | 달성 근거 |
|---|---|
| 오버셀 0건 | s01 RealFinal: orders_reserved=100, 5xx=0 (Atomic Update, PR #459) |
| 중복 주문 0건 | DB unique index `(fan_id, product_id, drop_event_id)` + 409 Conflict 응답 |
| 드롭스 스파이크 안전 처리 | s04: Nginx Rate Limit 5r/s, P95 278ms, spike_reserved=100 |
| SSE 2,000 동시 연결 | s05 17차 달성: capacity_fill + overflow_probe 2단계 구조 |
| Read SLO | s02 P95 66ms, s07 P95 16ms, s06 Feed P95 58ms |
| 결제 안정성 | s03 P95 1,590ms < 2,000ms, 에러율 0.00% |
| 운영 접근 | SSM Session Manager — SSH 키 없이 전체 EC2 운영 |
| 무중단 배포 | Nginx upstream hot-reload 방식 Blue/Green, 0~2초 다운타임 |

### 1-3. 우리가 타협한 것

| 타협 항목 | 이유 |
|---|---|
| ALB 없음 | ALB 고정 요금 ~22,600원/월 → 예산 90,000원 초과 |
| NAT Gateway 없음 | EC2 퍼블릭 서브넷 배치로 아웃바운드 처리, 추가 비용 절감 |
| Auto Scaling 없음 | 단일 인스턴스 검증이 목표, 수평 확장은 Phase 2 |
| Redis AUTH Token 없음 | 보안그룹으로 Redis 6379 EC2 SG에서만 접근 제한 적용 |
| CloudWatch 알람 미구성 | Prometheus/Grafana로 SLO 시각화 — CloudWatch는 구성 파일만 존재 |
| s01 P95 기준 폐기 | 409(동시성 충돌 정상 응답) 포함 시 P95 측정 왜곡 — 정합성 기준으로 전환 |
| s03 SLO 완화 | Toss API 외부 의존 — 300ms → 2,000ms, 팀 전원 합의 완료 |

### 1-4. 아직 못 한 것

| 미완 항목 | 현재 상태 |
|---|---|
| 분산 환경 오버셀 실측 | 단일 EC2 기준 검증만 완료. D 실험(EC2-2 앱 노드) 계획은 있었으나 Phase 4 기간 내 완전 검증 미완 |
| SseEmitterRegistry 멀티노드 | in-memory ConcurrentHashMap — 분산 시 SSE 이벤트 유실 가능 (코드 분석으로 한계 확인, 실측 미완) |
| CloudWatch 네임드 알람 | 구성 파일 존재(`infra/cloudwatch/`), 실제 알람 등록 미확인 |
| prod profile E2E 부하 테스트 | JWT 인증 포함 전체 흐름 검증 미완 |

### 1-5. 다음 단계에서 개선할 것

| 개선 항목 | 방향 |
|---|---|
| ALB + Auto Scaling | 예산 확보 시 ALB Target Group 스위칭 방식으로 Blue/Green 재설계 |
| SseEmitterRegistry | Redis Pub/Sub 기반 브로드캐스트로 멀티노드 SSE 지원 |
| Redis AUTH Token | ElastiCache AUTH Token 설정으로 보안 강화 |
| CloudWatch 알람 | CPU, RDS connections, Redis memory 알람 등록 |
| prod E2E 부하 테스트 | JWT 발급 자동화 + k6 prod profile 시나리오 |
| Read Replica / RDS 스펙 업 | db.t3.micro 처리량 한계 해소 |

---

## 2. AWS · 인프라 설계 예상 질문

### Q1. 왜 AWS를 사용했나요?

**질문 의도**  
부트캠프 기본 제공 클라우드를 그냥 쓴 건지, 실제 선택 이유가 있는지 확인.

**30초 답변**  
AWS는 팀에서 가장 레퍼런스가 많고, 오픈런 커머스 서비스의 실제 운영 환경과 가장 유사한 인프라를 구성할 수 있어 선택했습니다. EC2, RDS, ElastiCache, S3, SSM 등 실무에서 사용하는 구성 요소를 직접 다루는 것이 포트폴리오 목적에도 맞다고 판단했습니다.

**상세 답변**  
- AWS 계정은 부트캠프에서 제공받았으나, VPC 설계·서브넷 분리·보안그룹 규칙·IAM 역할 등은 팀이 직접 구성했습니다.
- 전용 VPC(`vpc-0fef7cb616a5fd333`, CIDR `10.0.0.0/16`)를 사용했으며 default VPC는 사용하지 않았습니다.
- RDS·Redis를 private subnet에 격리하고 EC2만 외부 진입점으로 구성하는 실무 패턴을 적용했습니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md`

**꼬리 질문 대비**  
- Q: GCP나 Azure는 왜 고려 안 했나요? → 팀 경험치와 레퍼런스 자료가 AWS에 집중되어 있었고, 부트캠프 제공 환경도 AWS였습니다.

---

### Q2. 왜 EC2 단일 서버 구조인가요? 실무라면 너무 단순한 것 아닌가요?

**질문 의도**  
단일 SPOF 구조의 한계를 알고 선택한 것인지 확인.

**30초 답변**  
맞습니다. EC2 단일 서버는 SPOF입니다. 이번 MVP에서는 예산(월 90,000원)과 개발 기간(5주) 제약 안에서, 다중 인스턴스 확장보다 "단일 인스턴스에서 정합성과 운영 가능성을 먼저 검증"하는 것을 목표로 삼았습니다. 다중 노드 확장은 Phase 2 개선 계획으로 명시해 두었습니다.

**상세 답변**  
- EC2-1(t3.medium)에 앱 서버·Nginx·Prometheus·Grafana·WireMock을 통합 운영했습니다.
- 단일 서버의 SPOF 한계를 인지하고 있으며, Blue/Green 배포 시 Nginx `proxy_next_upstream`으로 전환 순간의 불안정을 최소화했습니다.
- 실제 프로덕션 확장 시에는 ALB + Auto Scaling Group이 필요하며, 이는 발표에서 "한계와 개선 방향"으로 명시합니다.

**우리가 타협한 부분**  
단일 EC2 구조는 운영 안정성보다 검증 범위를 좁혀 핵심 비즈니스 로직(오버셀, 정합성, 대기열)을 먼저 검증하기 위한 의도적 선택입니다.

**다음 개선 방향**  
ALB + EC2 2대 이상 + Auto Scaling Group → Blue/Green을 ALB Target Group 교체 방식으로 재설계.

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 2-5`
- `docs/operations/ppt/ppt-presentation-final.md § 11-2`

---

### Q3. 실무에서는 단일 EC2가 SPOF 아닌가요?

**30초 답변**  
맞습니다. EC2-1이 다운되면 전체 서비스가 중단됩니다. 이 한계는 발표에서도 명시하고 있습니다. 실무에서는 ALB + 다중 EC2 + Auto Scaling이 필요하며, 이번 MVP에서는 예산 제약으로 단일 인스턴스에서 정합성 검증에 집중했습니다.

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 9`

---

### Q4. 왜 ALB를 사용하지 않았나요?

**질문 의도**  
고가용성 설계가 빠진 이유, 비용 계산이 실제로 되어 있는지 확인.

**30초 답변**  
ALB 고정 요금은 약 22,600원/월($0.0225/hr × 720h)으로, 현재 인프라 비용 72,000원에 더하면 94,600원이 되어 예산 90,000원을 초과합니다. 예산 안에서 ALB 기능을 Nginx upstream hot-reload로 대체해 0~2초 다운타임의 Blue/Green 배포를 구현했습니다.

**상세 답변**  
- ALB 없이 Nginx `fandrops-active.conf`를 배포 시마다 교체하는 방식으로 Blue/Green 포트 스위칭을 구현했습니다.
- `proxy_next_upstream error timeout http_502`로 전환 순간의 502를 자동 재시도합니다.
- `spring.lifecycle.timeout-per-shutdown-phase: 30s` Graceful Shutdown으로 처리 중 요청을 안전하게 완료합니다.

**비용 비교**  
| 구성 | 월 비용 | 예산 대비 |
|---|---|---|
| 현재 구성 | ~72,000원 | ✅ +18,000원 여유 |
| + ALB | ~94,600원 | ❌ 4,600원 초과 |

**다음 개선 방향**  
예산 확보 시 ALB Target Group 스위칭 방식으로 재설계 → 전환 다운타임 0초, 실제 HA 구현 가능.

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 1-2`

---

### Q5. 왜 NAT Gateway를 사용하지 않았나요?

**질문 의도**  
private subnet의 아웃바운드 트래픽 처리를 이해하는지 확인.

**30초 답변**  
NAT Gateway는 private subnet에 있는 리소스가 인터넷으로 아웃바운드 통신할 때 필요합니다. 이번 구조에서 EC2-1은 public subnet에 있어 IGW를 통해 직접 아웃바운드가 가능합니다. RDS·Redis는 인터넷 아웃바운드가 필요 없는 데이터 계층이므로 NAT Gateway 없이 운영 가능합니다.

**상세 답변**  
- EC2-1은 public subnet(`subnet-041237a8773d66e22`, 퍼블릭 IP 자동 할당 `true`)에 배치되어 IGW를 통한 직접 아웃바운드가 가능합니다.
- RDS는 private subnet(`subnet-02c8fcfd6d5136e53`, `subnet-0e7237696df3fcde9`)에서 EC2 SG(`sg-0632450dfad67c09a`) 3306 인바운드만 허용합니다.
- NAT Gateway 고정 요금은 약 $45/월로 예산 제약 내에서 불필요한 비용입니다.

**꼬리 질문 대비**  
- Q: EC2가 public subnet에 있으면 보안상 문제 아닌가요? → 보안그룹에서 80/443만 외부 오픈, SSH(22) 인바운드 없음, SSM으로만 접근 제어합니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md § 2`

---

### Q6. 왜 Auto Scaling을 적용하지 않았나요?

**질문 의도**  
트래픽 급증 대응 능력이 없다는 지적 가능성.

**30초 답변**  
Auto Scaling을 적용하려면 ALB가 필수이고 (ALB 없이는 새 인스턴스로 트래픽을 라우팅할 수 없습니다), ALB 자체가 예산 초과입니다. 이번 MVP에서는 Nginx Rate Limit(5r/s 주문, 10r/s 대기열)으로 앱 서버 유입을 제어해 단일 서버가 버틸 수 있는 수준으로 트래픽을 조절했습니다.

**상세 답변**  
- s04 드롭스 스파이크 시나리오에서 Nginx Rate Limit이 초과 요청을 429로 빠르게 거부했고, 앱 서버에는 약 5 RPS 수준만 유입되어 P95 278ms를 달성했습니다.
- 단일 인스턴스 처리 한계를 넘어서는 트래픽은 Rate Limit으로 제어하는 것이 Auto Scaling 없는 환경에서의 현실적 방어 전략입니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 3-5`
- `docs/operations/aws/nginx-bluegreen-strategy.md § 1-2`

---

### Q7. 왜 Kubernetes를 사용하지 않았나요?

**질문 의도**  
컨테이너 오케스트레이션을 고려하지 않은 이유 확인.

**30초 답변**  
Kubernetes는 다수의 컨테이너를 조율할 때 가치가 있습니다. 이번 MVP는 단일 Spring Boot 앱을 운영하는 구조이고, k8s 클러스터 자체의 운영 오버헤드(마스터 노드, etcd, CNI 등)가 검증 목표(정합성·SLO)와 무관한 복잡도를 추가합니다. 단일 EC2 + systemd 조합이 현재 규모에 더 적합합니다.

**상세 답변**  
- EKS 클러스터 기본 비용만 $0.10/hr × 720h = $72/월(~100,000원)로 예산 초과입니다.
- systemd 기반 Blue/Green은 Kubernetes rollout과 동일한 무중단 배포 목적을 달성하면서 운영 복잡도가 훨씬 낮습니다.
- Phase 2에서 마이크로서비스 분리·다중 노드 운영 단계에서 k8s 도입을 고려할 수 있습니다.

---

### Q8. RDS와 Redis를 private subnet에 둔 이유는 무엇인가요?

**30초 답변**  
외부 인터넷에서 직접 접근할 수 없도록 데이터 계층을 격리하기 위해서입니다. RDS·Redis는 EC2 보안그룹(`sg-0632450dfad67c09a`, `sg-034cbf9b614050986`)에서만 인바운드를 허용합니다. public endpoint를 열면 인증 우회·무차별 대입 공격에 노출될 수 있습니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md § 2, 4, 5`

---

### Q9. EC2만 public subnet에 둔 이유는 무엇인가요?

**30초 답변**  
Nginx가 80/443 포트로 외부 요청을 받아야 하기 때문에 EC2-1은 public subnet에서 퍼블릭 IP를 할당받아야 합니다. 대신 보안그룹에서 SSH(22) 인바운드를 열지 않았고, SSM Session Manager로만 관리 접근을 허용해 공격 표면을 최소화했습니다.

---

### Q10. SSH를 열지 않고 운영하는 것이 불편하지 않나요?

**질문 의도**  
SSM 운영 경험이 실제로 있는지, 편의성 vs. 보안 트레이드오프를 이해하는지 확인.

**30초 답변**  
처음에는 불편하지만, SSM의 장점이 더 큽니다. 키페어 분실·유출 위험이 없고, 모든 접근이 AWS CloudTrail에 기록되며, IAM 정책으로 팀원별 접근 권한을 세분화할 수 있습니다. 실제로 Windows 환경에서 인코딩 이슈를 겪었지만 `PYTHONUTF8=1 + --cli-input-json file://` 조합으로 해결했습니다.

**상세 답변**  
- 보안그룹 `sg-0fbc632fa599e1e64`에 SSH 22번 인바운드 규칙이 없습니다 (AWS CLI 확인 완료).
- EC2-1·EC2-2 모두 SSM PingStatus=Online 상태입니다.
- SSM `send-command`의 stdout 24KB 절단 한계를 k6 결과 수집에서 겪었고, Prometheus remote write로 우회했습니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 3-3`
- `docs/operations/aws/current-infra-state.md § 5`

---

### Q11. S3는 어떤 용도로 사용했나요?

**30초 답변**  
두 가지 용도입니다. 첫째, 배포 파이프라인에서 GitHub Actions가 빌드한 JAR를 S3에 업로드하고, EC2-1이 SSM RunCommand를 통해 S3에서 다운로드해 배포합니다. 둘째, 사용자 이미지 등 오브젝트 스토리지로 활용하며 presigned URL로만 접근합니다. S3 public access block은 전체 활성화 상태입니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md § 4`

---

### Q12. Redis AUTH Token을 사용하지 않은 것은 보안상 괜찮나요?

**질문 의도**  
Redis 보안 설정의 한계를 인지하는지 확인.

**30초 답변**  
현재 Redis AUTH Token은 적용하지 않았습니다. 다만 Redis 6379 포트는 보안그룹 `sg-034cbf9b614050986`에서 EC2 SG(`sg-0fbc632fa599e1e64`)에서만 인바운드를 허용해 외부 직접 접근은 차단됩니다. MVP 범위에서 보안그룹 격리로 최소 수준의 보안을 확보했고, AUTH Token 도입은 Phase 2 개선 항목입니다.

**꼬리 질문 대비**  
- Q: 내부망 침해 시 Redis 데이터가 노출되지 않나요? → 맞습니다. 이 점이 AUTH Token을 도입해야 하는 이유입니다. 프로덕션 진입 전에 반드시 설정해야 할 항목으로 문서화했습니다.

---

### Q13. HTTPS/TLS는 어디까지 적용했나요?

**30초 답변**  
`api.fandrops.site` 도메인에 HTTPS가 적용되어 있습니다. Nginx에서 TLS를 종료하고, Nginx → Spring Boot 내부 통신은 HTTP(127.0.0.1:8081/8082)입니다. k6 테스트도 `https://api.fandrops.site`를 기본 엔드포인트로 사용합니다. s01·s03·s07은 Nginx 우회 직접 접근(`http://10.0.1.114:8081`)으로 실행됩니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 4`
- `docs/operations/aws/current-infra-state.md § 7`

---

## 3. 보안 · 운영 접근 방식 예상 질문

### Q14. SSH 없이 SSM만으로 운영이 충분한가요?

**30초 답변**  
네, 충분합니다. SSM Session Manager는 EC2 접속, 명령 실행, 포트 포워딩을 모두 지원합니다. 실제로 k6 시나리오 실행, Nginx 설정 확인, 헬스체크, DB 조회까지 전부 SSM으로 수행했습니다. SSH보다 접근 감사 추적이 용이하다는 장점도 있습니다.

**꼬리 질문 대비**  
- Q: IAM 키 유출 시 더 위험하지 않나요? → SSH 키페어 유출과 동일한 위험입니다. IAM 역할 기반 접근(`devcos-team06`)은 최소 권한 원칙 적용이 가능하고, MFA 추가 강화도 용이합니다.

---

### Q15. Prometheus와 Grafana 포트(3000, 9090)가 퍼블릭으로 열려 있는 게 맞나요?

**질문 의도**  
보안 미흡 지적 가능성.

**30초 답변**  
현재 발표·데모 편의를 위해 퍼블릭으로 열려 있습니다. 이 점은 `current-infra-state.md`에도 "Open follow-up: 데모 목적 외라면 VPN/bastion으로 제한 권장"으로 명시되어 있습니다. 프로덕션 진입 전에는 접근을 알려진 IP나 VPN으로 제한해야 합니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md § 5`

---

## 4. 배포 · Blue/Green 예상 질문

### Q16. 단일 EC2 안에서 포트만 바꾸는 것도 Blue/Green이라고 할 수 있나요?

**질문 의도**  
Blue/Green의 본래 의미는 독립 인프라 단위 교체인데, 같은 EC2에서 포트만 바꾸는 것을 Blue/Green이라 부를 수 있는지 확인.

**30초 답변**  
좁은 의미의 Blue/Green(독립 인프라 단위 교체)과 다르다는 점을 인정합니다. 정확하게는 "단일 EC2 내 Nginx upstream 포트 스위칭 방식의 무중단 배포"입니다. 발표에서도 이를 "ALB 없는 Nginx 기반 Blue/Green 포트 스위칭"으로 표현합니다. 핵심 효과인 "구 버전에서 신 버전으로의 트래픽 단계적 전환, 0~2초 다운타임, 헬스체크 기반 롤백"은 구현했습니다.

**상세 답변**  
- 진정한 Blue/Green은 두 개의 독립 인프라에서 ALB가 트래픽을 전환합니다.
- 우리 구조: 동일 EC2 내 `:8081(blue)`↔`:8082(green)` 포트 스위칭, Nginx `active.conf` 교체 + `nginx reload`.
- ALB 없는 환경에서 동일한 목적(새 버전 검증 후 전환, 실패 시 롤백)을 구현한 실용적 대안입니다.

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 2-2, 4`

---

### Q17. 배포 중 장애가 발생하면 롤백은 어떻게 하나요?

**30초 답변**  
자동 롤백과 수동 롤백 두 가지가 있습니다. 자동: 새 슬롯 헬스체크(60초 대기)가 실패하면 배포 스크립트가 새 슬롯을 stop하고 Nginx는 구 슬롯을 계속 서비스합니다. 수동: `echo "blue" > /etc/fandrops/active-slot` + `active.conf` 이전 포트 복구 + `nginx reload`로 즉시 전환 가능합니다.

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 5`

---

### Q18. 수동 배포와 자동 배포의 차이는 무엇인가요?

**30초 답변**  
`develop` 브랜치 push 시 GitHub Actions가 빌드→S3 업로드→SSM RunCommand로 EC2 배포까지 자동화됩니다. `main` 브랜치는 수동 트리거(workflow_dispatch)로 최종 프로덕션 배포를 제어합니다.

**꼬리 질문 대비**  
- Q: 자동 배포가 실패하면 어떻게 되나요? → 헬스체크 실패 시 배포 스크립트가 exit 1로 종료되어 구 슬롯이 계속 서비스합니다. Nginx 설정은 변경되지 않습니다.

---

### Q19. 왜 Docker로 배포하지 않았나요?

**30초 답변**  
앱 서버를 Docker로 배포하면 컨테이너 이미지 빌드·레지스트리(ECR) 관리가 추가됩니다. systemd + JAR 직접 배포가 단순하고, Blue/Green 슬롯 전환도 `systemctl restart` 대신 `active.conf` 교체만으로 구현 가능해 더 가벼운 선택이었습니다. 모니터링 스택(Prometheus, Grafana, WireMock)은 Docker로 운영합니다.

---

## 5. 비용 · MVP 범위 타협 예상 질문

### Q20. 왜 ALB, NAT Gateway, Auto Scaling, Kubernetes를 모두 사용하지 않았나요?

**질문 의도**  
MVP 범위 설정의 근거와 우선순위를 확인.

**30초 답변**  
이번 MVP의 핵심 검증 목표는 "오픈런 트래픽에서 오버셀 0건, 결제/재고 정합성, 운영 가능성"이었습니다. ALB·NAT·ASG·k8s는 수평 확장을 위한 인프라이지만, 단일 인스턴스에서도 이 목표를 충분히 검증할 수 있습니다. 예산 제약이 있었고, 인프라 복잡도보다 비즈니스 로직 검증을 우선했습니다.

**상세 답변**  
- ALB: 예산 초과 (~22,600원/월 추가)
- NAT Gateway: EC2 public subnet 배치로 불필요
- Auto Scaling: ALB 없이 구현 불가, 단일 인스턴스 Nginx Rate Limit으로 대체
- Kubernetes: EKS 비용 초과, 단일 앱에 과도한 복잡도

**근거 문서**  
- `docs/operations/aws/nginx-bluegreen-strategy.md § 1-2`

---

### Q21. t3.small에서 t3.medium으로 변경한 이유는 무엇인가요?

**질문 의도**  
스케일업이 임시방편인지, 근거 있는 결정인지 확인.

**30초 답변**  
부하 테스트 중 JVM + Prometheus + Grafana + WireMock이 동시에 기동될 때 t3.small(2GB RAM)에서 OOM이 발생했습니다. t3.medium(4GB RAM)으로 스케일업 후 모든 프로세스가 안정적으로 기동되었고, 300~600 RPS 처리가 가능해졌습니다. t3.large 이상은 예산 한도를 초과합니다.

| 인스턴스 | RAM | 결과 |
|---|---|---|
| t3.small | 2GB | JVM + 모니터링 동시 기동 시 OOM |
| t3.medium | 4GB | 안정 기동, SLO 달성 가능 |
| t3.large | 8GB | 예산 초과 |

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 3-1`

---

### Q22. 발표에서 "운영 가능성"이라고 말해도 되는 근거는 무엇인가요?

**질문 의도**  
"운영 가능성"이 과장된 표현인지 확인.

**30초 답변**  
"MVP 수준의 운영 가능성"이라는 표현이 더 정확합니다. 구체적으로는: SSM으로 SSH 없는 접근 통제, Nginx Health Check 기반 롤백, Prometheus/Grafana로 P95·5xx·커넥션 지표 시각화, 장애 등급 정의(P0/P1/P2), 장애 대응 절차 문서화까지 갖춰진 상태입니다. 단일 EC2 SPOF와 CloudWatch 알람 미설정은 명시적 한계로 함께 설명합니다.

**근거 문서**  
- `docs/operations/observability-metrics.md`
- `docs/operations/incident-response.md`
- `docs/operations/failure-policy.md`

---

## 6. 모니터링 · 장애 대응 예상 질문

### Q23. Prometheus/Grafana를 붙였다고 운영 가능하다고 볼 수 있나요?

**질문 의도**  
모니터링 도구가 있다고 운영이 가능한 게 아님을 아는지 확인.

**30초 답변**  
Prometheus/Grafana를 붙인 것만으로는 충분하지 않습니다. 저희는 추가로: SLO 기준(Write P95<300ms, Read P95<120ms, 5xx<0.1%)을 정의했고, P0/P1/P2 장애 등급과 대응 절차를 `incident-response.md`에 문서화했으며, 실제 k6 부하 시나리오로 지표를 수집해 SLO 달성 여부를 확인했습니다. 다만 CloudWatch 알람 설정은 미완 항목으로 인정합니다.

**근거 문서**  
- `docs/operations/observability-metrics.md`
- `docs/operations/incident-response.md`

---

### Q24. 어떤 메트릭을 핵심으로 봤나요?

**30초 답변**  
크게 4가지입니다: ① HTTP P95 응답시간(Write<300ms, Read<120ms) ② 5xx 비율(<0.1%) ③ DB 커넥션 활성 수(HikariCP, 최대 30) ④ 오버셀·중복 주문 건수(부하 후 DB 교차 검증). 추가로 Redis 메모리, SSE 활성 연결 수, Outbox pending 건수를 보조 지표로 정의했습니다.

**근거 문서**  
- `docs/operations/observability-metrics.md § 1`

---

### Q25. Write P95 < 300ms 기준은 왜 설정했나요?

**30초 답변**  
오픈런 커머스에서 주문·결제는 팬이 체감하는 핵심 트랜잭션입니다. P95 300ms는 대부분의 사용자가 "빠르다"고 느끼는 경계값으로, 일반적인 웹 서비스 Write SLO 기준(200~500ms 범위)의 보수적 설정입니다. s03 결제는 Toss 외부 API 의존성을 고려해 2,000ms로 완화했습니다.

---

### Q26. Slack 알람 연동은 실제로 되어 있나요?

**질문 의도**  
발표에서 언급한 알람이 실제로 동작하는지 확인.

**30초 답변**  
`observability-metrics.md`에 Slack 연동이 DoD(완료 기준)로 정의되어 있으나, 현재 `current-infra-state.md` 기준으로 CloudWatch 네임드 알람은 발견되지 않았습니다. Prometheus Alertmanager와 Grafana 알람 패널은 구성되어 있습니다. Slack 실시간 알람은 확인 필요 항목입니다. (확인 필요)

---

### Q27. CloudWatch Agent는 사용했나요?

**30초 답변**  
CloudWatch Agent 설정 파일은 `infra/cloudwatch/`에 존재합니다. 다만 실제 CloudWatch 네임드 알람은 AWS CLI 조회 시 `fandrops` 관련 알람이 확인되지 않았습니다. 호스트 레벨 메트릭 수집 용도로 구성했으나, 실시간 알람은 Prometheus/Grafana를 주로 사용합니다.

**근거 문서**  
- `docs/operations/aws/current-infra-state.md § 9`

---

### Q28. 장애 등급 P0/P1/P2는 어떻게 나눴나요?

**30초 답변**  
P0는 서비스 전체 불가·오버셀 발생·DB 다운, P1은 SLO 위반 지속·Redis 다운·DB 커넥션 고갈, P2는 성능 저하·캐시 히트율 하락·슬로우 쿼리입니다. 오버셀·중복 결제는 P0로 즉각 대응합니다.

**근거 문서**  
- `docs/operations/incident-response.md`
- `docs/operations/failure-policy.md`

---

### Q29. Redis가 죽으면 어떻게 동작하나요?

**30초 답변**  
`failure-policy.md`에 정의된 대로 fail-fast입니다. Redis가 다운되면 대기열 join/SSE 연결이 즉시 503을 반환하고, Nginx Rate Limit이 트래픽 제한을 강화합니다. Redis는 대기열·Rate Limit·캐시의 핵심 의존성이므로 Redis 없이 DB 대기열로 폴백하는 방식은 MVP 범위에서 채택하지 않았습니다.

**근거 문서**  
- `docs/operations/failure-policy.md § 3.1`

---

### Q30. DB 커넥션 풀이 고갈되면 어떻게 하나요?

**30초 답변**  
부하 테스트 중 실제로 커넥션 풀 고갈(`Connection is not available, request timed out`)을 겪었고, 이를 HikariCP `maximumPoolSize`를 기본값 10에서 30으로 증설해 해결했습니다. db.t3.micro MySQL의 `max_connections` 제약 내에서 30이 안정적인 최대값입니다. 30 이상으로 올리면 RDS가 커넥션 초과로 거부합니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 3-4`

---

## 7. k6 부하 테스트 예상 질문

### Q31. 왜 k6를 선택했나요?

**30초 답변**  
k6는 JavaScript 기반의 시나리오 작성이 직관적이고, `--out xk6-prometheus-rw`로 Prometheus remote write를 기본 지원해 Grafana 시각화와 연동이 용이합니다. 또한 EC2에서 직접 실행 시 단일 바이너리로 의존성 없이 동작하고, VU·RPS·ramping 등 다양한 부하 패턴을 지원합니다.

---

### Q32. GitHub Actions로 k6를 돌리면 안 되나요?

**질문 의도**  
EC2-2를 별도로 쓰는 이유를 이해하는지 확인.

**30초 답변**  
안 되는 건 아니지만, GitHub Actions runner는 미국 리전(us-east-1 등)에서 실행되어 서울 리전 EC2까지 왕복 지연이 추가됩니다. 100~200ms의 대륙 간 지연이 P95에 더해지면 SLO 달성 여부를 정확히 판단하기 어렵습니다. 또한 runner의 네트워크 대역폭이 일정하지 않아 측정값 변동성도 큽니다.

**실제 경험**  
- GitHub Actions는 workflow 레벨 smoke test와 s05 SSE(GHA IP 분산 특성 활용)에만 사용했습니다.
- SLO 판단 기준이 되는 공식 수치는 모두 EC2-2(서울 리전, 동일 VPC) 실행 결과입니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 3-6`
- `docs/operations/k6/k6-actions-runner.md`

---

### Q33. 앱 서버와 k6 테스트 서버를 분리한 이유는 무엇인가요?

**30초 답변**  
동일 EC2에서 k6를 실행하면 k6 프로세스가 앱 서버와 CPU·메모리를 경쟁합니다. 이 경우 측정된 P95는 "앱의 실제 처리 성능"이 아니라 "CPU 경합 상태에서의 성능"이 됩니다. EC2-2를 분리해 k6가 CPU를 독점 사용하도록 하고, EC2-1은 앱 처리에만 집중할 수 있도록 했습니다.

---

### Q34. 같은 EC2에서 k6를 돌리면 왜 문제가 되나요?

**30초 답변**  
k6는 수백 VU를 생성할 때 CPU를 많이 사용합니다. EC2-1 t3.medium(2 vCPU)에서 k6와 Spring Boot가 동시에 CPU를 사용하면 Spring Boot의 응답 시간이 인위적으로 늘어납니다. 이렇게 측정된 SLO 수치는 실제 서비스 성능을 반영하지 못합니다.

---

### Q35. 테스트 시나리오 s01~s07은 각각 무엇을 검증하나요?

**30초 답변**  

| 시나리오 | 검증 목적 | 핵심 SLO |
|---|---|---|
| s01 주문 동시성 | 100명 동시 주문 시 오버셀 0건 | orders_reserved=100, 5xx=0 |
| s02 피드 Read | 피드 목록 조회 Read P95 | P95 < 120ms |
| s03 결제 확인 | 결제 확인 API P95 | P95 < 2,000ms, 에러율 < 0.1% |
| s04 드롭스 스파이크 | 오픈런 스파이크 Write P95 | P95 < 300ms, spike_reserved=100 |
| s05 SSE 대기열 | 2,000 동시 SSE 연결 | 정상 구간 거부 0건, 초과 시 429 retryable:true |
| s06 통합 워크로드 | 복합 시나리오 동시 SLO | Feed<120ms, Payment<2,000ms, Order=200 |
| s07 상품 조회 처리량 | 300 RPS constant-arrival-rate | P95 < 120ms |

---

### Q36. 부하 테스트에서 무엇을 성공 기준으로 봤나요?

**30초 답변**  
시나리오별로 다릅니다. Read 계열(s02, s07)은 P95 < 120ms + 에러율 < 0.1%, Write(s04)는 P95 < 300ms, 결제(s03)는 P95 < 2,000ms, 주문 동시성(s01)은 orders_reserved=100 + 5xx=0 (P95는 참고 지표), SSE(s05)는 2,000 동시 연결 + 초과 시 429 retryable:true입니다.

---

### Q37. 오버셀 0은 어떻게 검증했나요?

**질문 의도**  
단순히 k6 에러율 0%가 아니라 DB 수준에서 재고 수량을 확인했는지 확인.

**30초 답변**  
s01 완료 후 DB에서 `orders` 테이블의 status=RESERVED 건수와 `inventory` 테이블의 `reserved_qty`를 직접 조회해 교차 검증했습니다. k6의 `orders_reserved` 커스텀 메트릭도 100으로 집계됩니다. s01 RealFinal: orders_reserved=100, 5xx=0 (재고 100개 기준).

---

### Q38. P95는 어디서 확인했나요?

**30초 답변**  
공식 수치는 k6 클라이언트 집계 기준입니다. k6 종료 후 stdout 출력의 `http_req_duration p(95)` 값을 사용합니다. Grafana Micrometer 패널은 실시간 추세 모니터링에 사용하며, Prometheus stale series 이슈로 인해 k6 종료 후 instant query 대신 `max_over_time(...[2h])` range query로 조회합니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 4`
- `docs/personal/jiyoungjae.md § 2`

---

### Q39. k6 결과와 Grafana 지표를 어떻게 연결했나요?

**30초 답변**  
k6가 `--out xk6-prometheus-rw`로 EC2-1의 Prometheus(`http://10.0.1.114:9090/api/v1/write`)에 실시간으로 메트릭을 push합니다. Grafana는 이 Prometheus를 데이터소스로 연결해 k6 메트릭과 Spring Boot Micrometer 메트릭을 같은 대시보드에서 시각화합니다.

---

### Q40. 테스트 데이터는 어떻게 준비했나요?

**30초 답변**  
fan_id 1~2100에 대한 JWT 토큰을 `/opt/fandrops/k6/seed/tokens.csv`에 사전 생성해 EC2-2에 배치했습니다. 각 시나리오 실행 전 DB seed 스크립트로 product·inventory를 초기화하고, Redis에 Access Ticket을 적재(`access:ticket:{productId}:{fanId}`)합니다.

**꼬리 질문 대비**  
- Q: 같은 데이터로 반복 실행하면 결과가 달라지지 않나요? → 각 측정 전 inventory를 초기화하고 Redis 티켓을 재적재합니다. 동일 조건을 재현할 수 있습니다.

---

### Q41. prod JWT 인증 때문에 테스트가 막힌 문제는 어떻게 해결했나요?

**질문 의도**  
실제 prod 환경을 그대로 테스트했는지 확인.

**30초 답변**  
새벽 시간대에 서버를 local profile로 전환해 `X-Fan-Id` 헤더로 인증을 우회하는 방식으로 테스트했습니다. local profile에서는 `X-Fan-Id` 헤더에 fan_id를 직접 주입하면 인증 없이 API를 호출할 수 있습니다. 이 방식은 인증 자체의 성능 측정이 목적이 아닌, 주문·재고·SSE의 동시성 검증이 목적이었기 때문에 타당한 접근입니다.

**우리가 타협한 부분**  
운영 인증 흐름(JWT 발급 → 토큰 포함 요청) 전체를 포함한 E2E 부하 검증은 후속 개선 과제입니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 4`

---

### Q42. local profile로 테스트한 것이 실제 운영 검증이라고 볼 수 있나요?

**질문 의도**  
prod profile과 다른 환경에서 테스트한 결과의 신뢰성 확인.

**30초 답변**  
prod profile은 JWT 인증이 필수이므로 대량 테스트 사용자 토큰 발급 자동화가 필요했습니다. 이번 부하 테스트의 핵심 목적은 인증 자체가 아니라 주문/재고/SSE/대기열의 **동시성 검증**이었습니다. local profile 전환 시 인증 필터만 우회되며, 비즈니스 로직(재고 차감, 오버셀 방지, 대기열 처리)은 동일하게 실행됩니다. 다만 운영 인증 흐름 포함 E2E 검증은 후속 과제입니다.

---

### Q43. GitHub Actions 미국 리전에서 한국 서버를 때리는 테스트의 한계는 무엇인가요?

**30초 답변**  
GitHub Actions runner는 미국(us-east-1 등) 리전에서 실행됩니다. 서울 리전 EC2까지 대륙 간 왕복 지연이 100~200ms 추가됩니다. 이 경우 측정된 P95는 "앱 처리 시간 + 네트워크 지연"의 합이 됩니다. 한국 사용자가 체감하는 성능과는 다른 수치입니다. 공식 SLO 측정에는 동일 리전·VPC 내의 EC2-2를 사용했습니다.

---

## 8. SSE · 대기열 예상 질문

### Q44. SSE 테스트에서 unexpected EOF가 나왔는데 실패 아닌가요?

**질문 의도**  
SSE 연결 종료 방식과 테스트 메트릭 해석을 이해하는지 확인.

**30초 답변**  
k6는 SSE를 브라우저처럼 장기 연결을 유지하지 않습니다. 첫 청크(초기 `waiting` 이벤트)를 받은 후 루프를 돌아 재연결을 시도합니다. 따라서 k6 입장에서는 서버가 연결을 유지하는 도중 VU 루프가 종료되면 "unexpected EOF"처럼 보입니다. 이는 k6 SSE 테스트 구조의 특성이며, 실제 서버 측에서는 정상적으로 연결이 맺어지고 유지됩니다. s05에서 이 문제를 해결하기 위해 `capacity_fill + overflow_probe` 2단계 구조로 재설계했습니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § s05 사례 2`
- `docs/operations/k6/k6-s05-sse-queue-troubleshooting.md`

---

### Q45. SSE ramping-vus 구조적 결함이란 무엇인가요?

**30초 답변**  
`ramping-vus` 방식에서 VU가 감소할 때 진행 중인 SSE 연결이 interrupt로 종료됩니다. SSE는 연결 완료 시 성공 메트릭을 기록하는데, interrupt로 종료되면 성공·실패 어느 쪽도 기록되지 않아 모든 메트릭이 0/0이 됩니다. 이를 해결하기 위해 VU가 감소하지 않는 2단계 구조로 재설계했습니다: ① capacity_fill(2,000 VU 채우기) → ② overflow_probe(초과 구간 429 검증).

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 사례 2`

---

### Q46. 대기열 테스트는 정확히 무엇을 검증한 건가요?

**30초 답변**  
두 가지를 검증했습니다. ① 정상 구간: 2,000명이 동시에 SSE 연결을 맺을 때 서버가 거부 없이 수용할 수 있는가 (capacity_fill). ② 초과 구간: 2,001번째 이후 요청이 429 응답과 `retryable:true`를 올바르게 반환하는가 (overflow_probe). 최종 결과: 2,000 동시 연결 ✅, 429 retryable:true 비율 99.33% ✅.

---

### Q47. SSE는 HTTP와 다른 점이 무엇인가요? (심화 꼬리 질문)

**30초 답변**  
HTTP 요청/응답은 완료되면 연결이 닫힙니다. SSE는 HTTP 응답을 닫지 않고 서버가 계속 데이터를 push합니다. 따라서 ① HTTP/1.1 chunked transfer encoding이 필수 (HTTP/1.0 미지원, Nginx `proxy_http_version 1.1` 필요), ② 서버가 클라이언트 disconnect를 능동적으로 감지하지 못해 heartbeat가 필요, ③ 연결 수가 서버 FD(파일 디스크립터) 한계에 직접 영향합니다.

---

## 9. 인증 · 프로파일 전환 예상 질문

### Q48. 새벽 시간대에 local profile로 전환한 이유는 무엇인가요?

**30초 답변**  
prod profile에서 대량 테스트를 하면 실제 사용자 요청과 섞여 서비스 영향이 생길 수 있습니다. 새벽(트래픽이 없는 시간)에 서버를 local profile로 전환해 테스트 전용 상태로 만든 후 측정했습니다. 측정 완료 후 prod profile로 원복했습니다.

---

### Q49. prod profile 그대로 테스트하지 않은 이유는 무엇인가요?

**30초 답변**  
prod profile은 JWT 인증 + 실제 Toss API 호출이 필요합니다. fan_id 1~2100의 유효한 JWT 토큰을 자동 발급하는 파이프라인이 없었고, 실제 Toss API에 수백 건의 결제 요청을 보내는 것은 현실적으로 불가능합니다. 결제 시나리오는 WireMock으로 Toss API를 대체했습니다.

---

## 10. 성능 지표 · 검증 결과 예상 질문

### Q50. P95, 5xx, 오버셀 0 같은 지표는 어떻게 확인했나요?

**30초 답변**  
세 가지 방법을 조합했습니다. ① k6 클라이언트 집계: 시나리오 종료 후 stdout의 `http_req_duration p(95)`, `http_req_failed` 값 직접 확인. ② DB 교차 검증: `SELECT COUNT(*) FROM orders WHERE status='RESERVED'`로 오버셀 여부 확인. ③ Grafana 실시간 시각화: Prometheus remote write → Grafana 패널에서 추세 모니터링.

---

### Q51. SLO 완화(s01 P95 폐기, s03 2,000ms)는 기술적 한계를 인정한 건 아닌가요?

**질문 의도**  
SLO 완화가 실패를 가리기 위한 사후 조작인지 확인.

**30초 답변**  
s01 P95 폐기는 측정 기준의 정합성 문제입니다. 동시성 테스트에서 409(정상 동시성 충돌 응답)가 P95에 포함되면 "속도가 느리다"는 결론이 나오지만, 이는 동시성 처리의 정상 동작입니다. 동시성 테스트의 본질 목적은 "몇 개가 예약되었는가"이므로 정합성 기준이 더 타당합니다. s03 2,000ms는 Toss API 외부 호출 한 번 왕복에 300ms는 물리적으로 달성 불가능한 기준이어서 실제 결제 서비스 SLA(1~3초)에 맞췄습니다. 두 완화 모두 팀 전원 합의를 거쳤습니다.

**근거 문서**  
- `docs/operations/ppt/ppt-presentation-final.md § 10`

---

### Q52. s02 피드 Read가 Final에서 276ms였다가 RealFinal에서 66ms로 급격히 개선된 이유는 무엇인가요?

**30초 답변**  
FeedLikeCache(PR #394) 적용 때문입니다. FeedCache(피드 목록)는 적용되어 있었지만, 각 피드의 좋아요 여부(`applyIsLiked()`)를 확인할 때 Redis 추가 조회가 발생했습니다. `feed:liked:{fanId}:{sortedFeedIds}` 키로 좋아요 상태를 별도 캐싱하자, FeedCache hit + FeedLikeCache hit 경로에서 DB 쿼리 0회, Redis 추가 왕복 0회가 됩니다.

---

## 11. 아키텍처 선택 예상 질문

### Q53. 왜 MSA가 아니라 멀티모듈 모놀리스인가요?

**질문 의도**  
MSA가 더 현대적인데 왜 모놀리스를 선택했는지 확인.

**30초 답변**  
5명, 5주 규모에서 MSA는 서비스 간 통신, 분산 트레이싱, 독립 배포 파이프라인 등 오버헤드가 비즈니스 로직 개발보다 클 수 있습니다. 멀티모듈 모놀리스는 단일 JVM에서 실행하면서도 모듈 경계(domain/application/api/infrastructure)를 Gradle 의존성으로 강제해 도메인 격리 훈련 효과를 얻었습니다. ADR-001에 의사결정 근거가 문서화되어 있습니다.

**근거 문서**  
- `docs/adr/ADR-001-multi-module-monolith.md`

---

### Q54. 멀티모듈이면 실제로 어떤 장점이 있었나요?

**30초 답변**  
도메인 레이어에서 Spring·JPA import를 Gradle 컴파일 의존성으로 금지합니다. `order-domain`이 `payment-infrastructure`를 직접 참조하면 빌드가 실패합니다. 이를 통해 도메인 순수성(포트-어댑터 패턴)을 코드 수준에서 강제하고, 팀원이 타 도메인 인프라를 실수로 직접 호출하는 것을 방지했습니다.

---

### Q55. 장바구니를 Redis가 아니라 RDB에 둔 이유는 무엇인가요?

**30초 답변**  
Phase 1에서는 RDB로 시작해 성능 측정을 통해 Phase 2 전환 여부를 판단하는 전략(ADR-003)을 채택했습니다. 부하 테스트 결과 장바구니 쓰기 P95가 주문 생성 TX P95의 20% 미만이면 RDB를 유지합니다. Redis로 미리 전환하면 성능 문제가 있는지 없는지 실측 근거 없이 과설계하는 것입니다.

**근거 문서**  
- `docs/adr/ADR-003-cart-storage-rdb-phase1.md`

---

### Q56. 대기열은 왜 Redis가 적합한가요?

**30초 답변**  
대기열은 순위 조회(`ZRANK`, `ZRANGE`)·정렬 집합 연산·팬 진입/퇴장이 빈번한 구조입니다. Redis의 Sorted Set은 이를 O(log N)으로 처리합니다. RDB로 구현하면 `ORDER BY + LIMIT + 건별 rank 계산`이 필요해 동시 접속자가 많을 때 DB 부하가 급증합니다.

---

### Q57. DB 비관락과 Redis 분산락 중 무엇을 선택했나요?

**30초 답변**  
재고 차감은 Atomic Update(`UPDATE inventory SET available_qty = available_qty - 1 WHERE product_id = ? AND available_qty > 0`)를 사용합니다. DB 단건 UPDATE의 원자성으로 오버셀을 방지합니다. 결제는 `@Lock(PESSIMISTIC_WRITE)`를 사용합니다. 이 조합으로 단일 EC2 환경에서 오버셀 0건·중복 결제 0건을 달성했습니다.

---

## 12. 타협 · 한계 · 개선 예상 질문

### Q58. 이번 프로젝트에서 가장 큰 인프라 타협은 무엇인가요?

**30초 답변**  
단일 EC2 SPOF 구조입니다. ALB 없이 Nginx Blue/Green을 구현했지만, EC2-1이 다운되면 Nginx도 함께 내려가 전체 서비스가 중단됩니다. 실무 수준의 고가용성을 위해서는 ALB + 다중 EC2가 필수이며, 이는 명시적으로 Phase 2 개선 항목으로 분리했습니다.

---

### Q59. 실무 수준으로 가려면 무엇을 추가해야 하나요?

**30초 답변**  
우선순위 순서로: ① ALB + EC2 최소 2대(실제 HA), ② Auto Scaling Group(트래픽 급증 대응), ③ Redis AUTH Token + 보안 강화, ④ CloudWatch 알람(CPU, RDS connections, Redis memory), ⑤ SseEmitterRegistry → Redis Pub/Sub(SSE 멀티노드), ⑥ prod profile E2E 부하 테스트.

---

### Q60. 부하 테스트 결과를 더 신뢰성 있게 만들려면 무엇이 필요한가요?

**30초 답변**  
세 가지입니다. ① prod profile 그대로 JWT 인증 포함 테스트 (현재는 local profile). ② 더 긴 steady-state 구간 측정 (현재는 peak 구간 중심). ③ cold cache 구간 처리 검증 (현재 s02·s07은 warm cache 기준). 이 세 가지를 추가하면 실제 운영 환경에 더 가까운 수치를 얻을 수 있습니다.

---

## 13. 발표 중 피해야 할 표현과 대체 표현

| 피해야 할 표현 | 이유 | 대체 표현 |
|---|---|---|
| "실무 수준의 인프라를 구축했습니다" | 단일 EC2 SPOF, ALB 없음 → 실무 수준과 차이 있음 | "MVP 범위의 운영 가능한 인프라를 구축했습니다" |
| "완전한 Blue/Green 배포를 구현했습니다" | 동일 EC2 포트 스위칭 — 진정한 Blue/Green과 다름 | "Nginx upstream 포트 스위칭 방식의 무중단 배포를 구현했습니다" |
| "운영 가능합니다" | CloudWatch 알람 미완, 단일 SPOF | "단일 EC2 환경에서 MVP 수준의 운영 체계를 갖췄습니다" |
| "고가용성을 달성했습니다" | ALB 없음 = HA 아님 | "무중단 배포를 구현했고, 고가용성은 Phase 2 목표입니다" |
| "테스트가 완벽하게 통과했습니다" | SLO 완화 2건 + local profile 기반 | "완화된 SLO 기준에서 전 시나리오를 달성했습니다" |
| "분산 환경에서도 검증했습니다" | D 실험 완전 검증 미완 | "단일 EC2 기준으로 검증했고, 분산 환경 검증은 Phase 2 계획입니다" |
| "시간이 없어서 못 했습니다" | 변명처럼 들림 | "이번 MVP 범위에서는 예산/기간 제약으로 우선순위를 조정했고, Phase 2 개선 항목으로 분리했습니다" |

---

## 14. 최종 Q&A 치트시트

| 질문 | 핵심 답변 한 줄 |
|---|---|
| 왜 단일 EC2? | 예산 90,000원 + 정합성 검증 우선, SPOF 한계는 명시적 인정 |
| 왜 ALB 없음? | ALB 고정 ~22,600원/월 → 예산 초과, Nginx 포트 스위칭으로 대체 |
| Blue/Green이 맞나요? | 엄밀히는 Nginx upstream 포트 스위칭, 효과(무중단·롤백)는 동일 |
| 왜 t3.medium? | t3.small OOM → 스케일업, t3.large는 예산 초과 |
| SSM 왜 씀? | 키페어 유출 위험 제거, IAM 기반 접근 제어 |
| k6 EC2-2 왜 분리? | CPU 경합 제거 → 정확한 SLO 측정값 확보 |
| GitHub Actions k6 한계? | 미국 리전 → 100~200ms 왕복 지연 추가로 SLO 판단 부적합 |
| 오버셀 0 어떻게 확인? | k6 orders_reserved 메트릭 + DB orders 테이블 직접 조회 교차 검증 |
| local profile 왜 씀? | JWT 대량 발급 불가 + Toss API 실제 호출 불가 → 동시성 검증이 핵심 |
| SLO 완화 정당한가? | s01 409=정상 응답 포함 P95 왜곡, s03 Toss API 물리 한계 → 팀 합의 완료 |
| Redis 죽으면? | Fail-fast: 대기열·Rate Limit 503, Nginx 트래픽 제한 강화 |
| MSA 안 쓴 이유? | 5명 5주 → 분산 오버헤드 > 이득, ADR-001에 근거 문서화 |
| Prometheus로 충분? | SLO 시각화는 충분, CloudWatch 알람은 미설정 한계 명시 |
| SSE unexpected EOF? | k6의 VU interrupt 특성, 서버 측 정상 연결 — 2단계 구조로 해결 |
| 분산 환경은? | 단일 EC2 검증 완료, SseEmitterRegistry in-memory 분산 한계 문서화 |
| 다음 단계는? | ALB + ASG + Redis Pub/Sub SSE + CloudWatch 알람 + prod E2E 테스트 |

---

## 발표자가 기억해야 할 핵심 프레임

### 1. 우리가 한 것

- 단일 EC2 기반이지만 실제 AWS 리소스(VPC, 서브넷, 보안그룹, RDS, ElastiCache, S3)를 직접 설계하고 구성했다.
- RDS·Redis를 private subnet에 격리하고 EC2만 외부 진입점으로 구성했다.
- SSH 없이 SSM Session Manager로만 운영 접근을 설계했다.
- Nginx upstream 포트 스위칭으로 0~2초 다운타임의 Blue/Green 배포를 구현했다.
- k6를 EC2-2(동일 VPC)에서 실행해 GitHub Actions 대비 정확한 SLO 수치를 확보했다.
- Prometheus/Grafana로 P95, 5xx, 커넥션, SSE, Redis, JVM 지표를 시각화했다.
- s01~s07 전 시나리오에서 SLO를 달성했다 (s01 정합성 기준, s03 SLO 완화 포함).

### 2. 우리가 일부러 안 한 것

- ALB / Auto Scaling / NAT Gateway / Kubernetes는 MVP 범위에서 제외했다.
- 이유는 예산(~72,000원/월), 기간(5주), 팀 규모(5명), 검증 목표(정합성 우선) 때문이다.
- 대신 Phase 2 개선 항목으로 명확히 분리하고 문서화했다.

### 3. 우리가 검증하려 한 것

- 오버셀 0건 (s01 RealFinal: orders_reserved=100, 5xx=0)
- 중복 주문 0건 (unique index + 409 Conflict)
- 결제/재고 정합성 (Atomic Update, PESSIMISTIC_WRITE)
- 드롭스 스파이크 안전 처리 (Nginx Rate Limit + P95 278ms)
- SSE 2,000 동시 연결 (17차 트러블슈팅)
- 앱 서버와 부하 발생 서버 분리의 필요성 (EC2-2 분리)
- 운영 지표 기반 설명 가능성 (Prometheus/Grafana SLO 시각화)

### 4. 답변할 때의 기본 구조

1. "맞다, 그 부분은 실무적으로 더 확장할 수 있다."
2. "하지만 이번 MVP에서는 예산/기간/목표상 여기까지를 범위로 잡았다."
3. "대신 이 범위 안에서 [구체적 수치]를 실제로 검증했다."
4. "다음 단계에서는 [구체적 개선 방향]으로 확장하면 된다."

---

## PPT에 바로 반영하면 좋은 Q&A TOP 10

1. **왜 ALB 없이 Blue/Green이라 부르나요?** → Nginx upstream 포트 스위칭 방식, 비용 계산 포함 설명
2. **오버셀 0은 어떻게 검증했나요?** → k6 orders_reserved + DB 교차 검증, Atomic Update
3. **local profile 테스트가 실제 운영 검증인가요?** → 동시성 목적, 인증 E2E는 후속 과제
4. **SLO 완화는 실패 인정 아닌가요?** → 409 정상 포함 P95 왜곡 + Toss 물리 한계, 팀 합의
5. **GitHub Actions k6는 왜 한계인가요?** → 미국 리전 왕복 지연, EC2-2 분리 이유
6. **t3.small → t3.medium 변경 이유는?** → OOM 실증 + 비용 비교표
7. **SSE 17차 시도를 왜 했나요?** → Nginx HTTP/1.0, limit_conn GHA IP, 2단계 구조
8. **Redis 죽으면 어떻게 되나요?** → fail-fast 정책, Nginx 제한 강화
9. **단일 EC2 SPOF 어떻게 방어할 건가요?** → 인정하고 Phase 2 ALB 계획 제시
10. **운영 가능성의 근거는?** → SSM, 장애 등급 정의, SLO 달성 수치, 장애 대응 문서

## 발표자가 특히 조심해야 할 표현 TOP 5

1. **"실무 수준의 인프라"** → 단일 SPOF + ALB 없음 → "MVP 수준의 운영 체계"로 대체
2. **"완전한 Blue/Green"** → 동일 EC2 포트 스위칭 — 진정한 B/G 아님 → "Nginx 포트 스위칭 방식의 무중단 배포"
3. **"고가용성"** → ALB 없으면 HA 불가 → "무중단 배포" 또는 "배포 안정성"
4. **"분산 환경에서 검증했습니다"** → D 실험 완전 검증 미완 → "단일 EC2에서 검증했고 분산 검증은 Phase 2 계획"
5. **"테스트가 완벽하게 통과"** → SLO 완화 2건 + local profile → "완화된 SLO 기준에서 전 시나리오 달성"

---

*참조 문서: `docs/operations/ppt/ppt-presentation-final.md`, `docs/operations/aws/current-infra-state.md`, `docs/operations/aws/nginx-bluegreen-strategy.md`, `docs/operations/observability-metrics.md`, `docs/operations/failure-policy.md`, `docs/operations/incident-response.md`, `docs/operations/k6/k6-baseline-results.md`, `docs/operations/k6/k6-tuned-results.md`, `docs/operations/k6/k6-final-results.md`, `docs/operations/k6/k6-realfinal-result.md`, `docs/operations/k6/k6-s05-sse-queue-troubleshooting.md`*
