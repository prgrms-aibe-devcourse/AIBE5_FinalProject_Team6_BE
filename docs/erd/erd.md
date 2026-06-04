---
## config:
  layout: elk
  theme: base
---

erDiagram
    FAN ||--o{ ORDER : places
    FAN ||--o{ RESTOCK_ALERT : subscribes
    FAN ||--o{ NOTIFICATION : receives
    FAN ||--o{ ATTENDANCE_LOG : "출석"
    FAN ||--o{ GOODS_VOTE_RECORD : "투표"

AGENCY_APPLICATION ||--o| AGENCY_ACCOUNT : "승인시 계정생성"
AGENCY_ACCOUNT ||--o{ ARTIST_PROFILE : "소속"
ARTIST_PROFILE ||--o{ ARTIST_MEMBER : "멤버"
ARTIST_PROFILE ||--o{ PRODUCT : owns
ARTIST_PROFILE ||--o{ ARTIST_FEED : posts
ARTIST_PROFILE ||--o{ ARTIST_NOTICE : publishes
ARTIST_PROFILE ||--o{ ARTIST_SCHEDULE : "등록"
ARTIST_PROFILE ||--o{ USER_FOLLOW : "팔로워"
ARTIST_PROFILE ||--o{ ATTENDANCE_EVENT : "출석이벤트"
ARTIST_PROFILE ||--o{ GOODS_VOTE : "굿즈투표"

ARTIST_MEMBER ||--o{ ARTIST_FEED : "작성"
ARTIST_MEMBER ||--o{ COMMENT : "답글"
ARTIST_MEMBER ||--o{ FEED_LIKE : "좋아요"

FAN ||--o{ USER_FOLLOW : "팔로우"
FAN ||--|| CART : "보유"
FAN ||--o{ COMMENT : writes
FAN ||--o{ FEED_LIKE : "좋아요"
FAN ||--o{ COMMENT_LIKE : "좋아요"

ARTIST_FEED ||--o{ COMMENT : has
ARTIST_FEED ||--o{ FEED_LIKE : receives
ARTIST_FEED ||--o{ FEED_IMAGE : "이미지"
COMMENT ||--o{ COMMENT_LIKE : receives
COMMENT ||--o{ COMMENT : "parent(대댓글)"
ARTIST_SCHEDULE ||--o{ NOTIFICATION : notifies

PRODUCT ||--o{ BANNER : "배너"
PRODUCT ||--|{ ORDER_ITEM : contains
PRODUCT ||--|| INVENTORY : "재고"
PRODUCT ||--o{ CART_ITEM : "담김"
ORDER ||--|{ ORDER_ITEM : aggregates
ORDER ||--|| PAYMENT : has
PRODUCT ||--o{ RESTOCK_ALERT : triggers
CART ||--o{ CART_ITEM : "담김"
INVENTORY ||--o{ INVENTORY_HISTORY : "이력"

ATTENDANCE_EVENT ||--o{ ATTENDANCE_LOG : "기록"
GOODS_VOTE ||--o{ GOODS_VOTE_OPTION : "선택지"
GOODS_VOTE ||--o{ GOODS_VOTE_RECORD : "기록"
GOODS_VOTE_OPTION ||--o{ GOODS_VOTE_RECORD : "선택"

ARTIST_NOTICE ||--o{ ARTIST_SCHEDULE : "일정연동"

FAN {
    bigint id PK
    string email
    string nickname
    varchar auth_provider "LOCAL | KAKAO | GOOGLE"
    varchar provider_id
    varchar password_hash
    boolean is_allow_notification "알림 수신 동의 (기본값: true)"
    datetime created_at
}

AGENCY_APPLICATION {
    bigint id PK
        string company_name "운영 주체 명칭(회사·활동명)"
    string business_registration_number "사업자등록번호"
    string representative_name "대표자명"
    string contact_email "담당자 연락 이메일"
    string contact_phone "담당자 연락 전화번호"
    string introduction "입점 신청 소개글"
    string target_artist_name "신청 대상 아티스트명"
    varchar status "PENDING | APPROVED | REJECTED"
    varchar reject_reason "반려 사유"
    datetime applied_at "신청 일시"
    datetime reviewed_at "심사 완료 일시"
}

AGENCY_ACCOUNT {
    bigint id PK
    string login_id
    varchar password_hash
    string company_name
    string contact_email
    varchar status "APPROVED"
    varchar invitation_token
    datetime token_expired_at
    varchar role "ROLE_AGENCY"
    datetime created_at
}

ARTIST_PROFILE {
    bigint id PK
    bigint agency_id FK
    string name
    int fan_count
    varchar homepage_url
    varchar youtube_url
    varchar instagram_url
    varchar profile_image_url
    varchar cover_image_url
    string bio
    datetime joined_at
}

ARTIST_MEMBER {
    bigint id PK
    bigint artist_id FK
    string login_id
    varchar password_hash
    string member_name
    varchar role "ROLE_ARTIST"
    varchar profile_image_url "개별 멤버 프로필 이미지"
    datetime created_at
}

ARTIST_FEED {
    bigint id PK
    bigint artist_id FK
    bigint artist_member_id FK
    string content
    int like_count
    int comment_count
    datetime created_at
}

FEED_IMAGE {
    bigint id PK
    bigint feed_id FK
    varchar image_url
    datetime created_at
}

ARTIST_NOTICE {
    bigint id PK
    bigint artist_id FK
    varchar type "GENERAL | LIVE | EVENT | DROP"
    string title
    string content
    varchar image_urls
    datetime created_at
}

COMMENT {
    bigint id PK
    bigint fan_id FK
    bigint artist_member_id FK
    bigint artist_id FK
    bigint feed_id FK
    bigint parent_id
    string content
    datetime created_at
}

FEED_LIKE {
    bigint id PK
    bigint fan_id FK
    bigint artist_member_id FK
    bigint artist_id FK
    bigint feed_id FK
}

COMMENT_LIKE {
    bigint id PK
    bigint fan_id FK
    bigint comment_id FK
}

PRODUCT {
    bigint id PK
    bigint artist_id FK
    string name
    decimal price
    varchar status "ON_SALE | SOLD_OUT"
    datetime drops_start_at
    datetime drops_end_at
    datetime updated_at
}

INVENTORY {
    bigint id PK
    bigint product_id FK
    int total_qty
    int reserved_qty
    int available_qty
    int version
    datetime updated_at
}

INVENTORY_HISTORY {
    bigint id PK
    bigint inventory_id FK
    varchar change_type "RESERVE | RELEASE | DECREASE | INCREASE | COMPENSATE"
    int qty_delta
    int qty_before
    int qty_after
    bigint reference_id
    varchar ref_type "ORDER | RESTOCK"
    datetime created_at
}

ORDER {
    bigint id PK
    bigint fan_id FK
    varchar idempotency_key
    varchar order_payment_key "서버 발급 주문 세션 키 (POST /orders 응답)"
    datetime created_at
    datetime updated_at
    string status "PENDING | RESERVED | PAID | FAILED | COMPLETED | CANCELLED"
    decimal total_amount
}

ORDER_ITEM {
    bigint id PK
    bigint order_id FK
    bigint product_id FK
    int quantity
    decimal price
}

PAYMENT {
    bigint id PK
    bigint order_id FK
    string payment_key
    string method
    string status "PENDING | SUCCESS | FAILED"
    datetime paid_at
    datetime failed_at
}

CART {
    bigint id PK
    bigint fan_id FK
    datetime created_at
    datetime updated_at
}

CART_ITEM {
    bigint id PK
    bigint cart_id FK
    bigint product_id FK
    int quantity
    datetime added_at
}

USER_FOLLOW {
    bigint id PK
    bigint fan_id FK
    bigint artist_id FK
    datetime followed_at
}

RESTOCK_ALERT {
    bigint id PK
    bigint fan_id FK
    bigint product_id FK
    varchar status "PENDING | SENT | CANCELLED"
    datetime created_at
}

ARTIST_SCHEDULE {
    bigint id PK
    bigint artist_id FK
    bigint notice_id FK
    varchar title
    varchar type "DROP | LIVE | EVENT | NOTICE"
    datetime scheduled_at
    boolean is_live "LIVE 타입 온에어 여부 (기본 false)"
}

BANNER {
    bigint id PK
    varchar banner_type "MAIN | STORE"
    bigint product_id FK
    string title
    varchar image_url
    varchar landing_url
    int exposure_order
    boolean is_active
    datetime start_at
    datetime end_at
}

NOTIFICATION {
    bigint notification_id PK
    bigint fan_id FK
    varchar notification_type "NEW_FEED | NEW_COMMENT | RESTOCK | ARTIST_SCHEDULE"
    bigint target_id
    varchar message
    boolean is_read
    datetime sent_at
}

ATTENDANCE_EVENT {
    bigint id PK
    bigint artist_id FK
    date start_date
    date end_date
    string reward_desc
    boolean is_active
}

ATTENDANCE_LOG {
    bigint id PK
    bigint event_id FK
    bigint fan_id FK
    date checked_date
}

GOODS_VOTE {
    bigint id PK
    bigint artist_id FK
    string title
    datetime ends_at
    boolean is_active
}

GOODS_VOTE_OPTION {
    bigint id PK
    bigint vote_id FK
    string label
    varchar image_url
    int vote_count
}

GOODS_VOTE_RECORD {
    bigint id PK
    bigint vote_id FK
    bigint option_id FK
    bigint fan_id FK
}

