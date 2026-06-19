#!/bin/bash
set -euo pipefail

JAR_DIR="/opt/fandrops"
ACTIVE_SLOT_FILE="/etc/fandrops/active-slot"
NGINX_ACTIVE_CONF="/etc/nginx/fandrops-active.conf"

# S3_BUCKET은 호출 시 환경변수로 주입
: "${S3_BUCKET:?S3_BUCKET 환경변수가 필요합니다}"

# ── 1. 현재 슬롯 확인 ────────────────────────────────────────
ACTIVE=$(cat "$ACTIVE_SLOT_FILE" 2>/dev/null || echo "blue")
if [ "$ACTIVE" = "blue" ]; then
    NEW_SLOT="green"; NEW_PORT=8082
    OLD_SLOT="blue";  OLD_PORT=8081
else
    NEW_SLOT="blue";  NEW_PORT=8081
    OLD_SLOT="green"; OLD_PORT=8082
fi
echo "현재 active: $ACTIVE | 배포 대상: $NEW_SLOT (:$NEW_PORT)"

# ── 2. S3에서 새 JAR 다운로드 ───────────────────────────────
aws s3 cp "s3://$S3_BUCKET/deploy/api-server.jar" "/tmp/api-server.jar"
cp "/tmp/api-server.jar" "$JAR_DIR/$NEW_SLOT.jar"
chown fandrops:fandrops "$JAR_DIR/$NEW_SLOT.jar"

# ── 3. 새 슬롯 기동 ─────────────────────────────────────────
systemctl restart "fandrops-$NEW_SLOT"

# ── 4. 헬스체크 (최대 60초) ─────────────────────────────────
HEALTH="DOWN"
for i in $(seq 1 24); do
    HEALTH=$(curl -sf "http://127.0.0.1:$NEW_PORT/actuator/health" \
             | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" \
             2>/dev/null || echo "DOWN")
    echo "[$i/24] health=$HEALTH"
    [ "$HEALTH" = "UP" ] && break
    sleep 5
done

if [ "$HEALTH" != "UP" ]; then
    echo "❌ 헬스체크 실패 → 롤백: $NEW_SLOT 종료"
    echo "--- journalctl ($NEW_SLOT, 최근 50줄) ---"
    journalctl -u "fandrops-$NEW_SLOT" -n 50 --no-pager || true
    systemctl stop "fandrops-$NEW_SLOT" || true
    exit 1
fi
echo "✅ 헬스체크 통과: $NEW_SLOT (:$NEW_PORT)"

# ── 5. Nginx 설정 파일 동기화 ────────────────────────────────
aws s3 cp "s3://$S3_BUCKET/deploy/nginx/fandrops-upstream.conf" /etc/nginx/conf.d/fandrops-upstream.conf
aws s3 cp "s3://$S3_BUCKET/deploy/nginx/fandrops-location.conf" /etc/nginx/default.d/fandrops-location.conf
echo "✅ Nginx 설정 파일 동기화 완료"

# ── 6. Nginx upstream 전환 ───────────────────────────────────
cat > "$NGINX_ACTIVE_CONF" << EOF
upstream fandrops_backend {
    server 127.0.0.1:$NEW_PORT;
    keepalive 32;
}
EOF

nginx -t || {
    echo "❌ nginx 설정 오류 → 이전 설정 복구"
    cat > "$NGINX_ACTIVE_CONF" << ROLLBACK
upstream fandrops_backend {
    server 127.0.0.1:$OLD_PORT;
    keepalive 32;
}
ROLLBACK
    exit 1
}
systemctl reload nginx
echo "✅ Nginx → :$NEW_PORT ($NEW_SLOT)"

# ── 7. 구 슬롯 Graceful Shutdown ────────────────────────────
systemctl stop "fandrops-$OLD_SLOT" || true
echo "✅ 구 슬롯 종료: $OLD_SLOT (:$OLD_PORT)"

# ── 8. 슬롯 기록 갱신 ───────────────────────────────────────
echo "$NEW_SLOT" > "$ACTIVE_SLOT_FILE"
echo "✅ 배포 완료. Active: $NEW_SLOT (:$NEW_PORT)"
