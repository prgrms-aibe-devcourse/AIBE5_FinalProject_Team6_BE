import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const SRC = path.join(ROOT, 'apps/api-server/src/main/resources/seed/test-data.sql');
const DEST = path.join(__dirname, 'demo-seed-prod-mysql.sql');

let text = fs.readFileSync(SRC, 'utf8');

text = text.replace(/\r\n/g, '\n');
text = text.replace(/-- 0\. ShedLock[\s\S]*?\);\n\n/, '');
text = text.replace(/-- Local dev seed[\s\S]*?-- =+\n\n/, '');
text = text.replace(/-- =+\n-- 테스트 계정[\s\S]*?-- =+\n\n/, '');

text = text.replace(
  /DATEADD\('(\w+)',\s*(-?\d+),\s*(NOW\(\)|CURRENT_DATE)\)/g,
  (_, unit, n, base) => {
    const b = base === 'NOW()' ? 'NOW()' : 'CURDATE()';
    const num = parseInt(n, 10);
    if (num < 0) return `DATE_SUB(${b}, INTERVAL ${Math.abs(num)} ${unit})`;
    return `DATE_ADD(${b}, INTERVAL ${num} ${unit})`;
  }
);
text = text.replace(/CURRENT_DATE/g, 'CURDATE()');

const HEADER = `-- ============================================================
-- FANDROPS 데모/QA 시드 데이터 (MySQL / RDS용)
-- 원본: apps/api-server/src/main/resources/seed/test-data.sql (로컬 H2)
-- 변환: H2 DATEADD -> MySQL DATE_ADD/DATE_SUB
--
-- ⚠️ 주의
--   - 운영 RDS에 실제 사용자 데이터가 있으면 실행 금지
--   - 스테이징/데모 DB 또는 팀 합의 후 1회 실행 권장
--   - 스키마는 Flyway 마이그레이션 완료 후 실행
--   - 이미지 URL은 placehold.co (실제 이미지는 추후 등록)
--
-- 테스트 계정 (비밀번호 공통: Test1234!)
--   fan@fandrops.test / fan2@fandrops.test
--   agency@fandrops.test
--   admin@fandrops.com (V18 마이그레이션 계정 비밀번호 UPDATE)
--   artist (아티스트 멤버, NOVA)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- [선택] 데모 DB 초기화 — 기존 시드 제거 후 재삽입 (합의 후 주석 해제)
/*
DELETE FROM feed_image;
DELETE FROM comment_like;
DELETE FROM comment;
DELETE FROM feed_like;
DELETE FROM goods_vote_record;
DELETE FROM goods_vote_option;
DELETE FROM goods_vote WHERE id = 1 OR artist_id IN (1,2);
DELETE FROM attendance_log;
DELETE FROM attendance_event WHERE artist_id = 1;
DELETE FROM artist_schedule WHERE artist_id IN (1,2,3);
DELETE FROM inventory_history;
DELETE FROM restock_alert;
DELETE FROM payment;
DELETE FROM order_item;
DELETE FROM orders WHERE idempotency_key LIKE 'ord00000-%';
DELETE FROM cart_item;
DELETE FROM cart WHERE fan_id IN (1,2);
DELETE FROM inventory;
DELETE FROM product WHERE artist_id IN (1,2,3);
DELETE FROM notification WHERE fan_id = 1;
DELETE FROM agency_application;
DELETE FROM banner;
DELETE FROM user_follow WHERE fan_id = 1 AND artist_id = 1;
DELETE FROM artist_feed WHERE artist_id IN (1,2,3);
DELETE FROM artist_member WHERE id = 1;
DELETE FROM artist_profile WHERE id IN (1,2,3);
DELETE FROM fan WHERE id IN (1,2);
DELETE FROM agency_account WHERE id = 1;
*/

SET FOREIGN_KEY_CHECKS = 1;

`;

let out = HEADER + text;
const BC = '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re';

const reps = [
  [
    `VALUES (1, 'agency@fandrops.test',\n        '${BC}',\n        '테스트 기획사', 'agency@fandrops.test', 'ACTIVE', 'AGENCY', NOW());`,
    `VALUES (1, 'agency@fandrops.test',\n        '${BC}',\n        '테스트 기획사', 'agency@fandrops.test', 'ACTIVE', 'AGENCY', NOW())\nON DUPLICATE KEY UPDATE login_id=VALUES(login_id), password_hash=VALUES(password_hash), company_name=VALUES(company_name), contact_email=VALUES(contact_email), status=VALUES(status), role=VALUES(role);`,
  ],
  [
    `VALUES (1, 'fan@fandrops.test', '테스트팬', 'LOCAL', 'local-fe-1',\n        '${BC}',\n        true, NOW());`,
    `VALUES (1, 'fan@fandrops.test', '테스트팬', 'LOCAL', 'local-fe-1',\n        '${BC}',\n        true, NOW())\nON DUPLICATE KEY UPDATE email=VALUES(email), nickname=VALUES(nickname), password_hash=VALUES(password_hash);`,
  ],
  [
    `    (3, 1, 'ECHO', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 ECHO');`,
    `    (3, 1, 'ECHO', 0, '2024-01-01 00:00:00', 'FE 검증용 테스트 아티스트 ECHO')\nON DUPLICATE KEY UPDATE name=VALUES(name), bio=VALUES(bio), agency_id=VALUES(agency_id);`,
  ],
  [
    `        '${BC}',\n        'NOVA 멤버', 'ARTIST');`,
    `        '${BC}',\n        'NOVA 멤버', 'ARTIST')\nON DUPLICATE KEY UPDATE member_name=VALUES(member_name), role=VALUES(role);`,
  ],
  [
    `VALUES (1, 1, NOW());`,
    `VALUES (1, 1, NOW())\nON DUPLICATE KEY UPDATE followed_at=VALUES(followed_at);`,
  ],
  [
    `VALUES (1, 1, 'NOVA 컴백 굿즈 — 어떤 디자인이 좋아요?', DATE_ADD(NOW(), INTERVAL 7 DAY), true, NOW());`,
    `VALUES (1, 1, 'NOVA 컴백 굿즈 — 어떤 디자인이 좋아요?', DATE_ADD(NOW(), INTERVAL 7 DAY), true, NOW())\nON DUPLICATE KEY UPDATE title=VALUES(title), ends_at=VALUES(ends_at), is_active=VALUES(is_active);`,
  ],
  [
    `VALUES (2, 'fan2@fandrops.test', '테스트팬2', 'LOCAL', 'local-fe-2',\n        '${BC}',\n        true, NOW());`,
    `VALUES (2, 'fan2@fandrops.test', '테스트팬2', 'LOCAL', 'local-fe-2',\n        '${BC}',\n        true, NOW())\nON DUPLICATE KEY UPDATE email=VALUES(email), nickname=VALUES(nickname), password_hash=VALUES(password_hash);`,
  ],
];

for (const [old, neu] of reps) {
  if (!out.includes(old)) console.warn('WARN not found:', old.slice(0, 50));
  out = out.replace(old, neu);
}

const remaining = (out.match(/DATEADD\(/g) || []).length;
fs.writeFileSync(DEST, out, 'utf8');
console.log(`Written ${DEST} (${out.split('\n').length} lines), remaining DATEADD: ${remaining}`);
