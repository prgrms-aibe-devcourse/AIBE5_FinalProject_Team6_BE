#!/usr/bin/env bash
# EC2 k6 + Wiremock 환경 구성 스크립트 (issue #230)
#
# 사용법:
#   1. EC2에 infra/k6/ 디렉터리 복사
#      scp -r infra/k6 ec2-user@<EC2_IP>:/opt/fandrops/k6
#      또는: git clone 후 cp -r infra/k6 /opt/fandrops/k6
#
#   2. EC2에서 실행 (SSM Session Manager 또는 SSH)
#      cd /opt/fandrops/k6
#      chmod +x scripts/setup-ec2.sh
#      sudo bash scripts/setup-ec2.sh
#
# 종료 코드: 0=성공, 1=실패

set -euo pipefail

K6_DIR="$(cd "$(dirname "$0")/.." && pwd)"   # /opt/fandrops/k6 기준

echo "=== [1/4] k6 설치 ==="
if command -v k6 &>/dev/null; then
  echo "k6 이미 설치됨: $(k6 version)"
else
  sudo dnf install -y "https://dl.k6.io/rpm/repo.rpm"
  sudo dnf install -y k6
  echo "k6 설치 완료: $(k6 version)"
fi

echo ""
echo "=== [2/4] Docker 설치 및 기동 ==="
if ! command -v docker &>/dev/null; then
  sudo dnf install -y docker
fi
if ! sudo systemctl is-active --quiet docker; then
  sudo systemctl enable docker
  sudo systemctl start docker
fi
echo "Docker 상태: $(sudo systemctl is-active docker)"

echo ""
echo "=== [3/4] Wiremock 컨테이너 기동 ==="
if sudo docker ps -a --format '{{.Names}}' | grep -q '^wiremock$'; then
  echo "기존 wiremock 컨테이너 제거 후 재기동"
  sudo docker rm -f wiremock
fi

sudo docker run -d \
  --name wiremock \
  --restart unless-stopped \
  -p 8090:8080 \
  -v "${K6_DIR}/wiremock:/home/wiremock" \
  wiremock/wiremock:3.3.1 \
  --root-dir /home/wiremock \
  --global-response-templating

echo "Wiremock 컨테이너 기동 완료 (포트 8090)"

echo ""
echo "=== [4/4] 동작 검증 ==="
sleep 3

echo "--- k6 version ---"
k6 version

echo "--- Wiremock stub 응답 테스트 ---"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8090/v1/payments/confirm \
  -H 'Content-Type: application/json' \
  -d '{"paymentKey":"success-1-0","orderId":"order-1","amount":15000}')

if [ "$HTTP_STATUS" = "200" ]; then
  echo "Wiremock 응답 정상 (HTTP $HTTP_STATUS)"
else
  echo "Wiremock 응답 이상 (HTTP $HTTP_STATUS) — 컨테이너 로그 확인: sudo docker logs wiremock"
  exit 1
fi

echo ""
echo "=============================="
echo " 환경 구성 완료"
echo "=============================="
echo ""
echo "다음 단계 — 결제(03) 시나리오 실행 전 앱 서버 재기동:"
echo "  ACTIVE=\$(cat /etc/fandrops/active-slot)"
echo "  sudo sed -i 's|TOSS_API_BASE_URL=.*|TOSS_API_BASE_URL=http://localhost:8090|' /etc/fandrops/fandrops-prod.conf"
echo "  sudo systemctl restart \"fandrops-\$ACTIVE\""
echo ""
echo "Wiremock 원복 (결제 시나리오 종료 후):"
echo "  sudo sed -i 's|TOSS_API_BASE_URL=.*|TOSS_API_BASE_URL=https://api.tosspayments.com|' /etc/fandrops/fandrops-prod.conf"
echo "  sudo systemctl restart \"fandrops-\$ACTIVE\""
