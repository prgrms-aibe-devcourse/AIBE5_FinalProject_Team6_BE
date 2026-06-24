-- Demo seed: 신규 에이전시 3개 + 아티스트/크리에이터 11명 + 연관 데이터
-- 대상 환경: prod · stg (MySQL)
-- NOVA·LUNA·ECHO·PRISM(agency 1-2, artist 1-4) 이미 존재 가정 — 신규 데이터만 추가
-- ============================================================

-- 1. 신규 에이전시 3개 (ID 3, 4, 5)
INSERT IGNORE INTO agency_account (id, login_id, password_hash, company_name, contact_email, status, role, created_at)
VALUES
    (3, 'starmusic@fandrops.test',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '스타뮤직 엔터테인먼트', 'starmusic@example.com',  'ACTIVE', 'AGENCY', NOW()),
    (4, 'sandbox@fandrops.test',    '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '샌드박스 크리에이티브',   'sandbox@example.com',    'ACTIVE', 'AGENCY', NOW()),
    (5, 'vuniverse@fandrops.test',  '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '브이유니버스',             'vuniverse@example.com',  'ACTIVE', 'AGENCY', NOW());

-- 2. 신규 아티스트/크리에이터 프로필 (ID 5~15)
INSERT IGNORE INTO artist_profile (id, agency_id, name, fan_count, joined_at, profile_image_url, bio)
VALUES
    -- 스타뮤직 엔터테인먼트 (agency_id=3)
    (5,  3, 'SOLAR',           1250, NOW() - INTERVAL 10 DAY, 'https://placehold.co/150x150/FFE066/333333?text=SOLAR',   '스타뮤직의 차세대 5인조 하이틴 아이돌 SOLAR ☀️'),
    (6,  3, 'STELLA',           830, NOW() - INTERVAL 12 DAY, 'https://placehold.co/150x150/FFE066/333333?text=STELLA',  '밤하늘의 가장 밝은 별빛처럼 노래하는 보컬 그룹 STELLA ✨'),
    (7,  3, 'VORTEX',           620, NOW() - INTERVAL  8 DAY, 'https://placehold.co/150x150/333333/FFFFFF?text=VORTEX',  '강렬한 일렉트로닉 비트와 힙합 사운드로 무대를 압도하는 실력파 댄스 크루 VORTEX 🌪️'),
    -- 샌드박스 크리에이티브 (agency_id=4)
    (8,  4, '민우 Minwoo',     3400, NOW() - INTERVAL 15 DAY, 'https://placehold.co/150x150/A0D2EB/333333?text=Minwoo',  '최신 IT 기기 리뷰와 일상 브이로그를 전해드리는 민우입니다! 💻'),
    (9,  4, '소영 Soyoung',    4120, NOW() - INTERVAL 14 DAY, 'https://placehold.co/150x150/FFAAA6/333333?text=Soyoung', '데일리 메이크업 꿀팁과 유니크한 데일리룩 스타일링 소영 💄'),
    (10, 4, '동현 Donghyun',   2850, NOW() - INTERVAL 20 DAY, 'https://placehold.co/150x150/333333/FFFFFF?text=Donghyun','종합 게임 방송과 유쾌한 입담으로 소통하는 동현의 게이밍 채널 🎮'),
    (11, 4, '혜린 Hyerin',     3080, NOW() - INTERVAL  9 DAY, 'https://placehold.co/150x150/D4A5A5/FFFFFF?text=Hyerin',  '맛있는 쿡방과 신선한 레시피로 힐링을 주는 혜린의 달콤한 키친 🍳'),
    -- 브이유니버스 (agency_id=5)
    (12, 5, '리아 Lia',        4780, NOW() - INTERVAL 11 DAY, 'https://placehold.co/150x150/957DAD/FFFFFF?text=Lia',     '노래하는 파란 머리 요정 🧚‍♀️ 버튜버 싱어송라이터 리아입니다 🎵'),
    (13, 5, '하루 Haru',       3590, NOW() - INTERVAL 13 DAY, 'https://placehold.co/150x150/E8D7FF/333333?text=Haru',    '고양이 귀를 가진 츤데레 겜돌이 하루의 쉼터 🐱🎮'),
    (14, 5, '셀레네 Selene',   5120, NOW() - INTERVAL 16 DAY, 'https://placehold.co/150x150/B2F7EF/333333?text=Selene',  '마법 도서관의 200세 대마법사! 인간 세상 게임 탐방기 셀레네 🔮'),
    (15, 5, '네오 Neo',        4050, NOW() - INTERVAL  7 DAY, 'https://placehold.co/150x150/333333/FF007F?text=Neo',     '네온사인 번쩍이는 가상 도시에서 온 Cyberpunk DJ 네오 🎧⚡');

-- 3. 신규 아티스트/크리에이터 멤버 계정 (ID 10~22)
INSERT IGNORE INTO artist_member (id, artist_id, login_id, password_hash, member_name, role)
VALUES
    (10, 5,  'SolarJun',          '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '준',   'ARTIST'),
    (11, 5,  'SolarMin',          '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '민',   'ARTIST'),
    (12, 6,  'StellaHana',        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하나', 'ARTIST'),
    (13, 6,  'StellaYoon',        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '윤',   'ARTIST'),
    (14, 7,  'VortexKai',         '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '카이', 'ARTIST'),
    (15, 8,  'CreatorMinwoo',     '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '민우', 'ARTIST'),
    (16, 9,  'CreatorSoyoung',    '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '소영', 'ARTIST'),
    (17, 10, 'CreatorDonghyun',   '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '동현', 'ARTIST'),
    (18, 11, 'CreatorHyerin',     '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '혜린', 'ARTIST'),
    (19, 12, 'VtuberLia',         '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '리아', 'ARTIST'),
    (20, 13, 'VtuberHaru',        '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '하루', 'ARTIST'),
    (21, 14, 'VtuberSelene',      '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '셀레네','ARTIST'),
    (22, 15, 'VtuberNeo',         '$2a$10$IXraSx3hpYkrj8jRqqIsxOkdIfRCZKYxPafAJT7v3ZrlvB43gl7Re', '네오', 'ARTIST');

-- 4. 팔로우 (fan_id=1,2)
INSERT IGNORE INTO user_follow (fan_id, artist_id, followed_at)
VALUES
    (1, 5,  NOW()),
    (1, 8,  NOW()),
    (1, 12, NOW()),
    (2, 6,  NOW()),
    (2, 9,  NOW()),
    (2, 14, NOW());

-- 5. 신규 상품
INSERT IGNORE INTO product (artist_id, name, price, status)
VALUES
    (5,  'SOLAR 공식 야광 슬로건',                  15000, 'ON_SALE'),
    (5,  'SOLAR 1st Mini Album [SUN]',              18000, 'ON_SALE'),
    (6,  'STELLA 별빛 무드조명',                    28000, 'ON_SALE'),
    (7,  'VORTEX 스트릿 오버핏 후드',               49000, 'ON_SALE'),
    (8,  '민우 IT 크리에이티브 마우스패드',          19000, 'ON_SALE'),
    (9,  '소영 데일리 무드 립스틱',                  16000, 'ON_SALE'),
    (10, '동현 초고밀도 게이밍 장패드',              22000, 'ON_SALE'),
    (11, '혜린 핸드메이드 데코 앞치마',              25000, 'ON_SALE'),
    (12, '리아 1st EP [Virtual Voice] CD',          22000, 'ON_SALE'),
    (13, '하루 치즈고양이 아크릴 스마트톡',          12000, 'ON_SALE'),
    (14, '셀레네 마법 도서관 가죽 양장 다이어리',    18000, 'ON_SALE'),
    (15, '네오 사이버네틱 글로우 배지 세트',          9000, 'ON_SALE');

-- 6. 재고
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 150, 0, 150, 0 FROM product WHERE name = 'SOLAR 공식 야광 슬로건';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 200, 0, 200, 0 FROM product WHERE name = 'SOLAR 1st Mini Album [SUN]';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0 FROM product WHERE name = 'STELLA 별빛 무드조명';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 80,  0, 80,  0 FROM product WHERE name = 'VORTEX 스트릿 오버핏 후드';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 120, 0, 120, 0 FROM product WHERE name = '민우 IT 크리에이티브 마우스패드';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 150, 0, 150, 0 FROM product WHERE name = '소영 데일리 무드 립스틱';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0 FROM product WHERE name = '동현 초고밀도 게이밍 장패드';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 50,  0, 50,  0 FROM product WHERE name = '혜린 핸드메이드 데코 앞치마';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 300, 0, 300, 0 FROM product WHERE name = '리아 1st EP [Virtual Voice] CD';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 150, 0, 150, 0 FROM product WHERE name = '하루 치즈고양이 아크릴 스마트톡';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 200, 0, 200, 0 FROM product WHERE name = '셀레네 마법 도서관 가죽 양장 다이어리';
INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0 FROM product WHERE name = '네오 사이버네틱 글로우 배지 세트';

-- 7. 상품 이미지
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/FFE066/333333?text=SOLAR+Slogan',    0, true FROM product WHERE name = 'SOLAR 공식 야광 슬로건';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/FFE066/333333?text=SOLAR+Album',     0, true FROM product WHERE name = 'SOLAR 1st Mini Album [SUN]';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/FFE066/333333?text=STELLA+Light',    0, true FROM product WHERE name = 'STELLA 별빛 무드조명';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/333333/FFFFFF?text=VORTEX+Hoodie',   0, true FROM product WHERE name = 'VORTEX 스트릿 오버핏 후드';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/A0D2EB/333333?text=Minwoo+Pad',      0, true FROM product WHERE name = '민우 IT 크리에이티브 마우스패드';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/FFAAA6/333333?text=Soyoung+Tint',    0, true FROM product WHERE name = '소영 데일리 무드 립스틱';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/333333/FFFFFF?text=Donghyun+Pad',    0, true FROM product WHERE name = '동현 초고밀도 게이밍 장패드';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/D4A5A5/FFFFFF?text=Hyerin+Apron',    0, true FROM product WHERE name = '혜린 핸드메이드 데코 앞치마';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/957DAD/FFFFFF?text=Lia+EP+CD',       0, true FROM product WHERE name = '리아 1st EP [Virtual Voice] CD';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/E8D7FF/333333?text=Haru+Smarttok',   0, true FROM product WHERE name = '하루 치즈고양이 아크릴 스마트톡';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/B2F7EF/333333?text=Selene+Diary',    0, true FROM product WHERE name = '셀레네 마법 도서관 가죽 양장 다이어리';
INSERT INTO product_image (product_id, image_url, sort_order, is_primary)
SELECT id, 'https://placehold.co/600x600/333333/FF007F?text=Neo+Badge',        0, true FROM product WHERE name = '네오 사이버네틱 글로우 배지 세트';

-- 8. 아티스트 피드
INSERT INTO artist_feed (artist_id, artist_member_id, content, like_count, comment_count, created_at)
VALUES
    (5,  10, 'SOLAR의 데뷔 앨범 [SUN] 예약 판매가 드디어 시작되었습니다! ☀️ 정말 열심히 준비했으니 많이 사랑해주세요!',   154, 42, NOW() - INTERVAL 2 DAY),
    (6,  12, '오늘 밤 9시, 유튜브 라이브에서 만나요! 별빛들과 소통할 생각에 두근두근거리네요 ✨',                          210, 56, NOW() - INTERVAL 5 HOUR),
    (7,  14, 'VORTEX의 새로운 스트릿 퍼포먼스 영상이 곧 업로드됩니다. 다들 기대하셔도 좋습니다! 🌪️🔥',                    89, 21, NOW() - INTERVAL 1 DAY),
    (8,  15, '맥북 프로 M4 16인치 개봉기 & 한달 사용기! 프로필 링크에서 만나보세요. 💻',                                  320, 88, NOW() - INTERVAL 3 DAY),
    (9,  16, '올리브영 세일 추천템! 건성 피부를 위한 보습 정착템들만 모아봤어요 💄✨',                                      412,115, NOW() - INTERVAL 4 DAY),
    (10, 17, '스팀 신작 생존 공포 게임 켠왕 갑니다!! 다들 야식 준비하시고 이따 8시에 만나요 🎮🍿',                          289, 64, NOW() - INTERVAL 2 HOUR),
    (11, 18, '여름철 입맛을 돋우는 수제 레몬 딜 버터 만들기 🍋 이번 주 레시피 노트 공개합니다!',                            189, 32, NOW() - INTERVAL 5 DAY),
    (12, 19, '리아의 첫 EP 타이틀곡 [Virtual Voice] 티저 영상이 공개되었습니다 🧚‍♀️ 많이 들어주실 거죠?',                   540,122, NOW() - INTERVAL 1 DAY),
    (13, 20, '오늘 롤 랭크 게임 방송 도중 펜타킬 달성!! 하이라이트 클립 올려봅니다 후후 🐱🎮',                              290, 78, NOW() - INTERVAL 4 HOUR),
    (14, 21, '마법 다이어리 속 특별 스펠 🔮 오늘 하루도 평화롭길 바라요. 마법 가루 뾰로롱~',                                380, 94, NOW() - INTERVAL 6 DAY),
    (15, 22, '사이버 펑크 테크노 리믹스 셋업 완료! DJ 네오의 가상 클럽 파티 시작합니다 🎧⚡',                               241, 51, NOW() - INTERVAL 3 DAY);

-- 9. 댓글
INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT id, 5, 1, null, null, '데뷔 축하해요! 앨범 대박나자 SOLAR!', NOW()
FROM artist_feed WHERE content LIKE 'SOLAR의 데뷔 앨범%' AND artist_id = 5;

INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT c.feed_id, c.artist_id, null, 10, c.id, '감사합니다! 정말 열심히 할게요 ☀️', NOW() + INTERVAL 10 MINUTE
FROM comment c WHERE c.content = '데뷔 축하해요! 앨범 대박나자 SOLAR!' AND c.fan_id = 1;

INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT id, 12, 1, null, null, '목소리 너무 신비롭고 예뻐요 ㅠㅠ 무한반복 중!', NOW()
FROM artist_feed WHERE content LIKE '리아의 첫 EP 타이틀곡%' AND artist_id = 12;

INSERT INTO comment (feed_id, artist_id, fan_id, artist_member_id, parent_id, content, created_at)
SELECT c.feed_id, c.artist_id, null, 19, c.id, '신비한 파란 목소리 마음에 드셨다니 다행이에요 💙', NOW() + INTERVAL 15 MINUTE
FROM comment c WHERE c.content = '목소리 너무 신비롭고 예뻐요 ㅠㅠ 무한반복 중!' AND c.fan_id = 1;

-- 10. 굿즈 투표
INSERT INTO goods_vote (artist_id, title, ends_at, is_active, created_at)
VALUES (5, 'SOLAR 첫 공식 응원봉 컬러 투표 ☀️', NOW() + INTERVAL 10 DAY, true, NOW());

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '골드 옐로우', null, 240 FROM goods_vote WHERE title = 'SOLAR 첫 공식 응원봉 컬러 투표 ☀️';
INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '선셋 오렌지', null, 180 FROM goods_vote WHERE title = 'SOLAR 첫 공식 응원봉 컬러 투표 ☀️';

INSERT INTO goods_vote (artist_id, title, ends_at, is_active, created_at)
VALUES (14, '셀레네 가죽 다이어리 커버 각인 문양 선택 🔮', NOW() + INTERVAL 5 DAY, true, NOW());

INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '초승달 문양',    null, 420 FROM goods_vote WHERE title = '셀레네 가죽 다이어리 커버 각인 문양 선택 🔮';
INSERT INTO goods_vote_option (vote_id, label, image_url, vote_count)
SELECT id, '마법 마도서 문양', null, 380 FROM goods_vote WHERE title = '셀레네 가죽 다이어리 커버 각인 문양 선택 🔮';

-- 11. 아티스트 스케줄
INSERT INTO artist_schedule (artist_id, notice_id, title, type, scheduled_at, live_url, content)
VALUES
    (5,  null, 'SOLAR 데뷔 앨범 발매 쇼케이스',              'LIVE',
     NOW() + INTERVAL 3 DAY, 'https://live.starmusic.example.com/solar',
     'SOLAR의 역사적인 데뷔 무대! 실시간 쇼케이스 방송에 많은 참여 부탁드립니다.'),

    (8,  null, '민우 IT 데스크 셋업 실시간 Q&A',             'LIVE',
     NOW() + INTERVAL 1 DAY, 'https://youtube.com/minwoo/live',
     '여러분들이 많이 질문해주셨던 컴퓨터 및 조명 세팅에 대해 실시간 소통으로 답변 드립니다.'),

    (12, null, '[공지] 리아 첫 단독 가상 콘서트 [Virtual Sky]', 'NOTICE',
     NOW() - INTERVAL 2 DAY, null,
     '리아의 첫 버추얼 콘서트가 메타버스 플랫폼에서 열립니다. 자세한 예매 사이트 및 접속 방법은 추후 공지될 예정입니다.');