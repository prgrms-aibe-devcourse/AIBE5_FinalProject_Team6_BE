package com.fandrops.order.domain.port;

import com.fandrops.order.domain.StoreBanner;
import java.util.List;
import java.util.Optional;

public interface StoreBannerRepository {
    StoreBanner save(StoreBanner banner);
    Optional<StoreBanner> findById(Long id);
    List<StoreBanner> findActiveStoreBanners();
    List<StoreBanner> findAllStoreBanners();
    void deleteById(Long id);
}
