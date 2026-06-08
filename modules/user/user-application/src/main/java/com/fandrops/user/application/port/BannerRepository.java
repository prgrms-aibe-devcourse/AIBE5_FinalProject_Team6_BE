package com.fandrops.user.application.port;

import com.fandrops.user.domain.Banner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BannerRepository {
    Banner save(Banner banner);
    Optional<Banner> findById(Long id);
    List<Banner> findActiveMainBanners(LocalDateTime now);
    List<Banner> findAllMainBanners();
}