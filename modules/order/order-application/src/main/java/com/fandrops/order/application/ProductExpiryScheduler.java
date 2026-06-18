package com.fandrops.order.application;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.port.ProductRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 드롭스 종료 자동 처리 스케줄러.
 * dropsEndAt이 지난 ON_SALE 드롭스 상품을 1분 주기로 SOLD_OUT 전이한다.
 */
@Component
public class ProductExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductExpiryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final ProductRepository productRepository;

    public ProductExpiryScheduler(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "productExpiryScheduler", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")
    public void expireDrops() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Product> expired = productRepository.findExpiredDrops(now, BATCH_SIZE);
        for (Product product : expired) {
            try {
                product.markSoldOut();
                productRepository.save(product);
                log.info("[DropsExpiry] 상품 {} SOLD_OUT 전이 완료", product.getId());
            } catch (Exception e) {
                log.error("[DropsExpiry] 상품 {} SOLD_OUT 전이 실패", product.getId(), e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("[DropsExpiry] 만료 드롭스 {}건 처리 완료", expired.size());
        }
    }
}
