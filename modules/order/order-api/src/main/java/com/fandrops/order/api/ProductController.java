package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.api.dto.CreateProductRequest;
import com.fandrops.order.api.dto.UpdateProductRequest;
import com.fandrops.order.application.ProductService;
import com.fandrops.order.application.dto.CreateProductCommand;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.dto.ProductResponse;
import com.fandrops.order.application.dto.UpdateProductCommand;
import com.fandrops.order.domain.ProductStatus;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponse>> getProducts(
            @RequestParam(defaultValue = "regular") String type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProducts(cursor, size), traceId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProduct(id), traceId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        Long productId = productService.createProduct(new CreateProductCommand(
                request.getArtistId(), request.getName(),
                request.getPrice(), request.getTotalQty()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("productId", productId), traceId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProduct(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductStatus status = productService.updateProduct(new UpdateProductCommand(
                id, request.getName(), request.getPrice(), request.getStatus()));
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("productId", id, "status", status.name()), traceId()));
    }

    private String traceId() {
        return MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
    }
}
