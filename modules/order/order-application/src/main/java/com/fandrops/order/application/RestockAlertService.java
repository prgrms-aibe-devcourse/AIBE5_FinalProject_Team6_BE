package com.fandrops.order.application;

import com.fandrops.order.application.event.RestockAlertEvent;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.RestockAlert;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.exception.RestockAlertNotFoundException;
import com.fandrops.order.domain.port.InventoryIncreasePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import com.fandrops.order.domain.port.ProductRepository;
import com.fandrops.order.domain.port.RestockAlertRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

public class RestockAlertService {

    private final RestockAlertRepository restockAlertRepository;
    private final ProductRepository productRepository;
    private final InventoryIncreasePort inventoryIncreasePort;
    private final InventoryReadPort inventoryReadPort;
    private final ApplicationEventPublisher eventPublisher;

    public RestockAlertService(RestockAlertRepository restockAlertRepository,
                               ProductRepository productRepository,
                               InventoryIncreasePort inventoryIncreasePort,
                               InventoryReadPort inventoryReadPort,
                               ApplicationEventPublisher eventPublisher) {
        this.restockAlertRepository = restockAlertRepository;
        this.productRepository = productRepository;
        this.inventoryIncreasePort = inventoryIncreasePort;
        this.inventoryReadPort = inventoryReadPort;
        this.eventPublisher = eventPublisher;
    }

    /** 재입고 알림 구독 — 이미 PENDING이면 기존 alertId 반환(멱등). */
    @Transactional
    public Long subscribe(Long fanId, Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return restockAlertRepository.findPendingByFanIdAndProductId(fanId, productId)
                .orElseGet(() -> restockAlertRepository.save(RestockAlert.create(fanId, productId)))
                .getId();
    }

    /** 재입고 알림 구독 취소 — PENDING 알림을 CANCELLED로 전이. */
    @Transactional
    public void unsubscribe(Long fanId, Long productId) {
        RestockAlert alert = restockAlertRepository
                .findPendingByFanIdAndProductId(fanId, productId)
                .orElseThrow(() -> new RestockAlertNotFoundException(fanId, productId));
        alert.cancel();
        restockAlertRepository.save(alert);
    }

    /**
     * 재입고 처리 — 재고 증가 + SOLD_OUT → ON_SALE + PENDING 구독자 이벤트 발행.
     * @return 재입고 후 totalQty
     */
    @Transactional
    public int restock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        inventoryIncreasePort.increase(productId, quantity);

        if (product.getStatus() == ProductStatus.SOLD_OUT) {
            product.markOnSale();
            productRepository.save(product);
        }

        // TODO: 구독자 증가 시 saveAll() + 배치 이벤트 발행으로 전환 필요 (현재 N번 UPDATE)
        List<RestockAlert> pending = restockAlertRepository.findAllPendingByProductId(productId);
        for (RestockAlert alert : pending) {
            alert.markSent();
            restockAlertRepository.save(alert);
            eventPublisher.publishEvent(new RestockAlertEvent(alert.getFanId(), productId, product.getName()));
        }

        InventoryInfo info = inventoryReadPort.getByProductId(productId);
        return info.getTotalQty();
    }
}
