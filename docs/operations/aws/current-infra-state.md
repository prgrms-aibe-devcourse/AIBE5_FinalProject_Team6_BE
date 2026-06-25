# Current AWS / Infra State

> Verified: 2026-06-25 (KST) via AWS CLI and SSM.  
> Purpose: current-state reference for architecture, deployment, and k6 interpretation. Historical setup steps remain in the phase runbooks.

---

## 1. Account / Region

| Item | Value |
| --- | --- |
| AWS account | `495264909330` |
| IAM caller used for verification | `devcos-team06` |
| Primary region | `ap-northeast-2` |
| Service domain | `api.fandrops.site` |
| FE domain | `fandrops.site`, `www.fandrops.site` |

Route53 hosted zones containing `fandrops` were not found in this AWS account at verification time. DNS for `fandrops.site` is therefore managed outside the verified Route53 scope or under a differently named zone.

---

## 2. Network

| Resource | Value |
| --- | --- |
| VPC | `vpc-0fef7cb616a5fd333` |
| CIDR | `10.0.0.0/16` |
| Default VPC | `false` |

### Subnets

| Subnet | CIDR | AZ | Public IP on launch | Current use |
| --- | --- | --- | --- | --- |
| `subnet-041237a8773d66e22` | `10.0.1.0/24` | `ap-northeast-2a` | `true` | EC2 app/k6 instances |
| `subnet-0875ab712a7c0ed82` | `10.0.2.0/24` | `ap-northeast-2c` | `true` | Public spare subnet |
| `subnet-02c8fcfd6d5136e53` | `10.0.11.0/24` | `ap-northeast-2a` | `false` | RDS subnet group |
| `subnet-0e7237696df3fcde9` | `10.0.12.0/24` | `ap-northeast-2c` | `false` | RDS subnet group |

---

## 3. Compute

| Role | Instance | Type | Private IP | Public IP | Security group | Status |
| --- | --- | --- | --- | --- | --- | --- |
| App / Nginx / monitoring | `team06-fandrops` (`i-07d1c60d175cdb8ca`) | `t3.medium` | `10.0.1.114` | `43.203.3.196` | `fandrops-prod-sg-ec2` | running |
| k6 runner | `team06-fandrops-2` (`i-067eea702d856fa05`) | `t3.small` | `10.0.1.47` | `3.34.42.43` | `fandrops-prod-sg-ec2` | running |

Current app runtime on EC2-1:

| Item | Current value |
| --- | --- |
| Active slot | `blue` |
| Active upstream | `127.0.0.1:8081` |
| `fandrops-blue` | active, `/actuator/health` = `UP` |
| `fandrops-green` | stopped/failed at verification time; used as inactive deployment slot |
| Nginx | active |
| Blue/Green config | `/etc/fandrops/active-slot`, `/etc/nginx/fandrops-active.conf` |

The inactive slot is not expected to stay running after a completed deployment. The deployment script starts the inactive slot, checks health, switches Nginx, then stops the old slot.

---

## 4. Data / Storage

| Layer | Resource | Spec / version | Endpoint / port | Access |
| --- | --- | --- | --- | --- |
| DB | `fandrops-prod-mysql` | MySQL `8.0.46`, `db.t3.micro`, 20GB | `fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com:3306` | private, not public |
| Cache / queue | `fandrops-prod-redis-001` | Redis `7.1.0`, `cache.t3.micro` | `fandrops-prod-redis-001.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com:6379` | private |
| Object storage | `fandrops-prod-storage-495264909330-ap-northeast-2-an` | S3, `ap-northeast-2` | presigned URL access through app | public access block enabled |

S3 public access block is fully enabled: `BlockPublicAcls`, `IgnorePublicAcls`, `BlockPublicPolicy`, and `RestrictPublicBuckets`.

---

## 5. Security Groups

| Security group | Purpose | Key inbound rules |
| --- | --- | --- |
| `fandrops-prod-sg-ec2` (`sg-0fbc632fa599e1e64`) | EC2 app/k6 instances | `80/443` from `0.0.0.0/0`, `3000/9090` from `0.0.0.0/0`, `8090` from VPC, `8081/8082/3306` from EC2-2 private IP, `8080` from same SG |
| `fandrops-prod-sg-rds` (`sg-0632450dfad67c09a`) | RDS MySQL | `3306` from EC2 SG only |
| `fandrops-prod-sg-redis` (`sg-034cbf9b614050986`) | ElastiCache Redis | `6379` from EC2 SG only |

Open follow-up: Grafana `3000` and Prometheus `9090` are currently public on the EC2 security group. If these are not intentionally public for presentation/demo, restrict them to known IPs or VPN/bastion access.

---

## 6. Runtime Services on EC2-1

| Service | Runtime | Port |
| --- | --- | --- |
| Nginx | systemd | `80`, `443` |
| Spring Boot active slot | systemd `fandrops-blue` | `8081` |
| Spring Boot inactive slot | systemd `fandrops-green` | `8082` when deploying |
| WireMock | Docker `wiremock/wiremock:latest` | host `8090` -> container `8080` |
| Prometheus | Docker `prom/prometheus:v3.4.1` | `9090` |
| Grafana | Docker `grafana/grafana:12.0.1` | `3000` |

WireMock is used by payment-related k6 scenarios through the app server. k6 does not call WireMock directly.

---

## 7. k6 Execution Model

Current runner:

| Item | Value |
| --- | --- |
| Instance | `team06-fandrops-2` |
| k6 version | `k6 v2.0.0` |
| API connectivity | `https://api.fandrops.site/actuator/health` = `UP` |
| Direct active slot connectivity | `http://10.0.1.114:8081/actuator/health` = `UP` |

Interpretation rule:

- Use EC2-2 for latency-sensitive SLO measurements because it stays in the same AWS region/VPC and avoids GitHub Actions trans-Pacific latency.
- Use GitHub Actions runner for workflow-level checks and cases where regional network latency does not affect the decision.
- For `s05` SSE, capacity should be interpreted with server-side Nginx/app logs and accepted/rejected connection counts, not only the k6 summary.

---

## 8. Nginx / Rate Limit

Repository source of truth:

- `nginx/fandrops-upstream.conf`
- `nginx/fandrops-location.conf`
- `.github/scripts/bluegreen-deploy.sh`

Current production upstream is generated on EC2:

```nginx
upstream fandrops_backend {
    server 127.0.0.1:8081;
    keepalive 32;
}
```

Current rate-limit zones from the repo:

| Zone | Rate | Applied path |
| --- | --- | --- |
| `fandrops_order` | `5r/s`, burst `10` | `/api/v1/orders` |
| `fandrops_queue` | `10r/s`, burst `1000` | `/api/v1/queue/join` |
| `fandrops_payment` | `5r/s`, burst `10` | `/api/v1/payments/toss/confirm` |
| `fandrops_sse` | connection zone | `/api/v1/queue/stream` |

---

## 9. Monitoring / Alerting

| Item | Current observation |
| --- | --- |
| Prometheus | Running on EC2-1 Docker, host port `9090` |
| Grafana | Running on EC2-1 Docker, host port `3000` |
| CloudWatch alarms | No `fandrops` / `FANDROPS` named metric alarms found by AWS CLI query |
| CloudWatch agent config | Stored in `infra/cloudwatch/` |

Grafana/Prometheus remain the primary SLO visualization path for k6 work. CloudWatch is used for host/log observability setup, but no named FANDROPS CloudWatch metric alarms were verified.

---

## 10. Documentation Maintenance Rules

- Update this file when AWS resource IDs, instance sizes, public endpoints, active k6 runner strategy, or security group exposure changes.
- Keep phase runbooks as historical execution records.
- Keep final measurement numbers in `docs/operations/k6/k6-realfinal-result.md`.
- Keep user-facing architecture summaries in `docs/architecture/architecture.md`, linking back here for exact IDs.
