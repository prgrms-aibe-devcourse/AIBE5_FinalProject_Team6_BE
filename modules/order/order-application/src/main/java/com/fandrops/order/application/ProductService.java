package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateProductCommand;
import com.fandrops.order.application.dto.ProductImageResponse;
import com.fandrops.order.application.dto.ProductListItemResponse;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.dto.ProductResponse;
import com.fandrops.order.application.dto.UpdateProductCommand;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductImage;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.application.port.ProductCachePort;
import com.fandrops.order.domain.port.InventoryCreatePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import com.fandrops.order.domain.port.ProductImageRepository;
import com.fandrops.order.domain.port.ProductRepository;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryCreatePort inventoryCreatePort;
    private final InventoryReadPort inventoryReadPort;
    private final ProductImageRepository productImageRepository;
    private final ProductCachePort productCachePort;

    public ProductService(ProductRepository productRepository,
                          InventoryCreatePort inventoryCreatePort,
                          InventoryReadPort inventoryReadPort,
                          ProductImageRepository productImageRepository,
                          ProductCachePort productCachePort) {
        this.productRepository = productRepository;
        this.inventoryCreatePort = inventoryCreatePort;
        this.inventoryReadPort = inventoryReadPort;
        this.productImageRepository = productImageRepository;
        this.productCachePort = productCachePort;
    }

    @Transactional(readOnly = true)
    public ProductListResponse getProducts(String type, Long artistId, Long cursor, int size) {
        // 캐시 히트 시 즉시 반환 — product·image 쿼리 생략 (inventory는 캐시 포함, TTL 120s)
        return productCachePort.get(type, artistId, cursor, size).orElseGet(() -> {
            List<Product> products = "drops".equals(type)
                    ? productRepository.findDropsProducts(artistId, cursor, size)
                    : productRepository.findRegularProducts(artistId, cursor, size);
            List<Long> productIds = products.stream().map(Product::getId).toList();
            if (productIds.isEmpty()) {
                return new ProductListResponse(List.of(), null);
            }
            Map<Long, InventoryInfo> inventoryMap = inventoryReadPort.getByProductIds(productIds);
            Map<Long, String> thumbnailMap = productImageRepository.findThumbnailsByProductIds(productIds);
            List<ProductListItemResponse> items = products.stream()
                    .map(p -> ProductListItemResponse.from(p,
                            inventoryMap.getOrDefault(p.getId(), new InventoryInfo(0, 0, 0)),
                            thumbnailMap.get(p.getId())))
                    .toList();
            Long nextCursor = products.size() == size
                    ? products.get(products.size() - 1).getId()
                    : null;
            ProductListResponse result = new ProductListResponse(items, nextCursor);
            productCachePort.put(type, artistId, cursor, size, result);
            return result;
        });
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        InventoryInfo inventory = inventoryReadPort.getByProductId(productId);
        List<ProductImageResponse> images = productImageRepository.findByProductId(productId).stream()
                .map(ProductImageResponse::from)
                .toList();
        return new ProductResponse(
                product.getId(), product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus().name(),
                inventory.getTotalQty(), inventory.getReservedQty(), inventory.getAvailableQty(),
                product.getDropsStartAt(), product.getDropsEndAt(), product.getUpdatedAt(),
                images);
    }

    @Transactional
    public Long createProduct(CreateProductCommand command) {
        boolean hasStart = command.getDropsStartAt() != null;
        boolean hasEnd = command.getDropsEndAt() != null;
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException("dropsStartAt, dropsEndAt은 함께 입력해야 합니다.");
        }
        Product product = command.isDrops()
                ? Product.createDrops(command.getArtistId(), command.getName(), command.getPrice(),
                        command.getDropsStartAt(), command.getDropsEndAt())
                : Product.createRegular(command.getArtistId(), command.getName(), command.getPrice());
        Product saved = productRepository.save(product);
        inventoryCreatePort.createInventory(saved.getId(), command.getTotalQty());

        List<ProductImage> images = ProductImage.from(saved.getId(), command.getImageUrls());
        if (!images.isEmpty()) {
            productImageRepository.saveAll(images);
        }
        productCachePort.evictAll();
        return saved.getId();
    }

    @Transactional
    public ProductStatus updateProduct(UpdateProductCommand command) {
        boolean hasStart = command.getDropsStartAt() != null;
        boolean hasEnd   = command.getDropsEndAt()   != null;
        if (hasStart != hasEnd) {
            throw new IllegalArgumentException("dropsStartAt, dropsEndAt은 함께 입력해야 합니다.");
        }
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        product.update(command.getName(), command.getPrice(), command.getStatus(),
                command.getDropsStartAt(), command.getDropsEndAt());
        productRepository.save(product);

        if (command.getImageUrls() != null) {
            productImageRepository.deleteByProductId(command.getProductId());
            List<ProductImage> images = ProductImage.from(command.getProductId(), command.getImageUrls());
            if (!images.isEmpty()) {
                productImageRepository.saveAll(images);
            }
        }
        productCachePort.evictAll();
        return product.getStatus();
    }
}
