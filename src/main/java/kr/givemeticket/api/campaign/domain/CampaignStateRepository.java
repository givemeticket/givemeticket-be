package kr.givemeticket.api.campaign.domain;

import java.util.Optional;

/**
 * 키의 존재 자체가 "오픈됨"을 뜻한다.
 */
public interface CampaignStateRepository {

    void open(Long campaignId, CampaignState state);

    Optional<CampaignState> find(Long campaignId);

    void remove(Long campaignId);
}
