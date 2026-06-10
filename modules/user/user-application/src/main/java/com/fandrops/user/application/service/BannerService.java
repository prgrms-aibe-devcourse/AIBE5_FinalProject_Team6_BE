package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.BannerResult;
import com.fandrops.user.application.dto.CreateBannerCommand;
import com.fandrops.user.application.dto.UpdateBannerCommand;
import com.fandrops.user.application.exception.BannerNotFoundException;
import com.fandrops.user.application.port.BannerRepository;
import com.fandrops.user.domain.Banner;
import com.fandrops.user.domain.BannerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BannerService {

    private final BannerRepository bannerRepository;

    public BannerService(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    /** GET /banners/main — 활성 배너 조회 (비인증) */
    public List<BannerResult> getActiveMainBanners() {
        return bannerRepository.findActiveMainBanners(LocalDateTime.now())
                .stream()
                .map(BannerResult::from)
                .toList();
    }

    /** GET /admin/main-banners — 전체 배너 목록 (Admin) */
    public List<BannerResult> getAllMainBanners() {
        return bannerRepository.findAllMainBanners()
                .stream()
                .map(BannerResult::from)
                .toList();
    }

    /** POST /admin/main-banners — 배너 생성 (Admin) */
    @Transactional
    public BannerResult createBanner(CreateBannerCommand command) {
        if (command.startAt() != null && command.endAt() != null
                && command.startAt().isAfter(command.endAt())) {
            throw new IllegalArgumentException("배너 종료 시각이 시작 시각보다 이를 수 없습니다.");
        }
        Banner banner = Banner.builder()
                .bannerType(BannerType.MAIN)
                .title(command.title())
                .imageUrl(command.imageUrl())
                .landingUrl(command.landingUrl())
                .exposureOrder(command.exposureOrder())
                .isActive(true)
                .startAt(command.startAt())
                .endAt(command.endAt())
                .build();
        return BannerResult.from(bannerRepository.save(banner));
    }

    /** PATCH /admin/main-banners/{id} — 배너 수정 (Admin) */
    @Transactional
    public BannerResult updateBanner(Long id, UpdateBannerCommand command) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException("존재하지 않는 배너입니다. id=" + id));
        LocalDateTime effectiveStart = command.startAt() != null ? command.startAt().orElse(null) : banner.getStartAt();
        LocalDateTime effectiveEnd = command.endAt() != null ? command.endAt().orElse(null) : banner.getEndAt();
        if (effectiveStart != null && effectiveEnd != null && effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("배너 종료 시각이 시작 시각보다 이를 수 없습니다.");
        }
        banner.update(
                command.title(), command.imageUrl(), command.landingUrl(),
                command.exposureOrder(), command.isActive(),
                command.startAt(), command.endAt()
        );
        return BannerResult.from(bannerRepository.save(banner));
    }

    /** DELETE /admin/main-banners/{id} — soft delete (Admin) */
    @Transactional
    public void deleteBanner(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException("존재하지 않는 배너입니다. id=" + id));
        banner.deactivate();
        bannerRepository.save(banner);
    }
}
