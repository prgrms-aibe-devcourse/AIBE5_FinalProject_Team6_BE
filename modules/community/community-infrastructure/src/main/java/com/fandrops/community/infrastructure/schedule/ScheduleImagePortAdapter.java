package com.fandrops.community.infrastructure.schedule;

import com.fandrops.community.application.port.ScheduleImagePort;
import com.fandrops.community.infrastructure.schedule.jpa.ArtistScheduleImageJpaEntity;
import com.fandrops.community.infrastructure.schedule.jpa.ArtistScheduleImageJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
public class ScheduleImagePortAdapter implements ScheduleImagePort {

    private final ArtistScheduleImageJpaRepository imageJpaRepository;

    public ScheduleImagePortAdapter(ArtistScheduleImageJpaRepository imageJpaRepository) {
        this.imageJpaRepository = imageJpaRepository;
    }

    @Override
    public void saveAll(Long scheduleId, List<String> imageUrls) {
        List<ArtistScheduleImageJpaEntity> entities = IntStream.range(0, imageUrls.size())
                .mapToObj(i -> new ArtistScheduleImageJpaEntity(null, scheduleId, imageUrls.get(i), i))
                .toList();
        imageJpaRepository.saveAll(entities);
    }

    @Override
    public Map<Long, List<String>> findByScheduleIds(List<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }
        return imageJpaRepository.findByScheduleIdInOrderBySortOrder(scheduleIds).stream()
                .collect(Collectors.groupingBy(
                        ArtistScheduleImageJpaEntity::getScheduleId,
                        Collectors.mapping(ArtistScheduleImageJpaEntity::getImageUrl, Collectors.toList())
                ));
    }

    @Override
    public List<String> findByScheduleId(Long scheduleId) {
        return imageJpaRepository.findByScheduleIdOrderBySortOrder(scheduleId).stream()
                .map(ArtistScheduleImageJpaEntity::getImageUrl)
                .toList();
    }
}
