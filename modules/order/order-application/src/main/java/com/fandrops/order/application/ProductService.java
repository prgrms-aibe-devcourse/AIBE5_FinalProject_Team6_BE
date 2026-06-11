package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateProductCommand;
import com.fandrops.order.application.dto.ProductListItemResponse;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.dto.ProductResponse;
import com.fandrops.order.application.dto.UpdateProductCommand;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.port.InventoryCreatePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import com.fandrops.order.domain.port.ProductRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryCreatePort inventoryCreatePort;
    private final InventoryReadPort inventoryReadPort;

    public ProductService(ProductRepository productRepository,
                          InventoryCreatePort inventoryCreatePort,
                          InventoryReadPort inventoryReadPort) {
        this.productRepository = productRepository;
        this.inventoryCreatePort = inventoryCreatePort;
        this.inventoryReadPort = inventoryReadPort;
    }

    @Transactional(readOnly = true)
    public ProductListResponse getProducts(String type, Long cursor, int size) {
        List<Product> products = "drops".equals(type)
                ? productRepository.findDropsProducts(cursor, size)
                : productRepository.findRegularProducts(cursor, size);
        List<ProductListItemResponse> items = products.stream()
                .map(ProductListItemResponse::from)
                .toList();
        Long nextCursor = products.size() == size
                ? products.get(products.size() - 1).getId()
                : null;
        return new ProductListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        InventoryInfo inventory = inventoryReadPort.getByProductId(productId);
        return new ProductResponse(
                product.getId(), product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus().name(),
                inventory.getTotalQty(), inventory.getReservedQty(), inventory.getAvailableQty(),
                product.getDropsStartAt(), product.getDropsEndAt(), product.getUpdatedAt());
    }

    @Transactional
    public Long createProduct(CreateProductCommand command) {
        Product product = command.isDrops()
                ? Product.createDrops(command.getArtistId(), command.getName(), command.getPrice(),
                        command.getDropsStartAt(), command.getDropsEndAt())
                : Product.createRegular(command.getArtistId(), command.getName(), command.getPrice());
        Product saved = productRepository.save(product);
        inventoryCreatePort.createInventory(saved.getId(), command.getTotalQty());
        return saved.getId();
    }

    @Transactional
    public ProductStatus updateProduct(UpdateProductCommand command) {
        Product product = productRepository.findById(command.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        product.update(command.getName(), command.getPrice(), command.getStatus(),
                command.getDropsStartAt(), command.getDropsEndAt());
        return productRepository.save(product).getStatus();
    }
}
