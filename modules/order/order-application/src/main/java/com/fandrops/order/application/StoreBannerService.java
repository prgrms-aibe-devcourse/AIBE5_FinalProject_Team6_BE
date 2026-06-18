package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateStoreBannerCommand;
import com.fandrops.order.application.dto.StoreBannerResponse;
import com.fandrops.order.application.dto.UpdateStoreBannerCommand;
import com.fandrops.order.domain.StoreBanner;
import com.fandrops.order.domain.exception.StoreBannerNotFoundException;
import com.fandrops.order.domain.port.StoreBannerRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class StoreBannerService {

    private final StoreBannerRepository storeBannerRepository;

    public StoreBannerService(StoreBannerRepository storeBannerRepository) {
        this.storeBannerRepository = storeBannerRepository;
    }

    @Transactional(readOnly = true)
    public List<StoreBannerResponse> getActiveStoreBanners() {
        return storeBannerRepository.findActiveStoreBanners().stream()
                .map(StoreBannerResponse::from)
                .toList();
    }

    @Transactional
    public Long createStoreBanner(CreateStoreBannerCommand command) {
        StoreBanner banner = StoreBanner.create(
                command.getTitle(), command.getImageUrl(), command.getLandingUrl(),
                command.getExposureOrder(), command.getStartAt(), command.getEndAt(),
                command.getProductId());
        return storeBannerRepository.save(banner).getId();
    }

    @Transactional
    public void updateStoreBanner(UpdateStoreBannerCommand command) {
        StoreBanner existing = storeBannerRepository.findById(command.getBannerId())
                .orElseThrow(() -> new StoreBannerNotFoundException(command.getBannerId()));
        StoreBanner updated = existing.update(
                command.getTitle(), command.getImageUrl(), command.getLandingUrl(),
                command.getExposureOrder(), command.getStartAt(), command.getEndAt(),
                command.getProductId());
        storeBannerRepository.save(updated);
    }

    @Transactional
    public void deleteStoreBanner(Long bannerId) {
        StoreBanner existing = storeBannerRepository.findById(bannerId)
                .orElseThrow(() -> new StoreBannerNotFoundException(bannerId));
        storeBannerRepository.save(existing.deactivate());
    }
}
