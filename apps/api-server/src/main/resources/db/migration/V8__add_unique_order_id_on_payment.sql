-- P1 fix: payment.order_id 유니크 제약 추가
-- findByOrderId가 Optional<Payment>를 반환하므로 1주문=1결제 불변식을 DB 레벨에서 보장.
-- lazy-create 동시 INSERT 경쟁 시 두 번째 요청이 ConstraintViolationException으로 실패하여
-- 데이터 중복을 방지한다. 클라이언트 재시도 시 기존 레코드를 정상 반환.
ALTER TABLE payment ADD UNIQUE KEY uq_payment_order_id (order_id);