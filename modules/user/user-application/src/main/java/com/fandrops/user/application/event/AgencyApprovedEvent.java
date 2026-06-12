package com.fandrops.user.application.event;

/** 입점 승인 완료 시 발행. community 모듈이 수신하여 아티스트 공간을 활성화한다. */
public class AgencyApprovedEvent {

    private final Long artistId;
    private final Long agencyId;
    private final String artistName;

    public AgencyApprovedEvent(Long artistId, Long agencyId, String artistName) {
        this.artistId = artistId;
        this.agencyId = agencyId;
        this.artistName = artistName;
    }

    public Long getArtistId()    { return artistId; }
    public Long getAgencyId()    { return agencyId; }
    public String getArtistName() { return artistName; }
}
