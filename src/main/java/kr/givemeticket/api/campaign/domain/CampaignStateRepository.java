package kr.givemeticket.api.campaign.domain;

public interface CampaignStateRepository {

    void open(Long campaignId);

    boolean isOpen(Long campaignId);
}
