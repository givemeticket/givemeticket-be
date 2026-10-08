package kr.givemeticket.api.wish.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.wish.application.dto.response.WishStatusResponse;
import kr.givemeticket.api.wish.domain.Wish;
import kr.givemeticket.api.wish.domain.WishRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 찜은 몇 번을 눌러도 결과가 같아야 한다. 하트 연타나 재전송에 오류가 나면 안 된다.
 */
class WishServiceTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long ME = 7L;
    private static final Long OTHER = 8L;

    private final FakeWishRepository wishRepository = new FakeWishRepository();
    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final WishService service = new WishService(wishRepository, campaignRepository);

    @Test
    @DisplayName("찜하면 찜 수가 늘고, 다시 찜해도 하나만 남는다")
    void wishesIdempotently() {
        givenCampaign(CampaignStatus.OPEN);

        service.wish(ME, CAMPAIGN_ID);
        WishStatusResponse response = service.wish(ME, CAMPAIGN_ID);

        assertThat(response.wishCount()).isEqualTo(1);
        assertThat(response.wished()).isTrue();
    }

    @Test
    @DisplayName("동시에 들어온 같은 찜이 유니크 제약에 걸려도 성공으로 끝난다")
    void treatsConcurrentDuplicateAsSuccess() {
        givenCampaign(CampaignStatus.OPEN);
        wishRepository.racingDuplicate = true;

        WishStatusResponse response = service.wish(ME, CAMPAIGN_ID);

        assertThat(response.wished()).isTrue();
        assertThat(response.wishCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("찜 수는 여러 사람의 찜을 센다")
    void countsAllWishes() {
        givenCampaign(CampaignStatus.OPEN);
        service.wish(ME, CAMPAIGN_ID);
        service.wish(OTHER, CAMPAIGN_ID);

        assertThat(service.getWishStatus(CAMPAIGN_ID, ME))
                .isEqualTo(new WishStatusResponse(CAMPAIGN_ID, 2, true));
    }

    @Test
    @DisplayName("로그인하지 않고 조회하면 내 찜 여부는 비어 있다")
    void leavesWishedEmptyForGuest() {
        givenCampaign(CampaignStatus.OPEN);
        service.wish(OTHER, CAMPAIGN_ID);

        assertThat(service.getWishStatus(CAMPAIGN_ID, null))
                .isEqualTo(new WishStatusResponse(CAMPAIGN_ID, 1, null));
    }

    @Test
    @DisplayName("해제는 찜하지 않은 행사에 불러도 오류가 아니다")
    void unwishesIdempotently() {
        givenCampaign(CampaignStatus.OPEN);
        service.wish(ME, CAMPAIGN_ID);

        service.unwish(ME, CAMPAIGN_ID);
        WishStatusResponse response = service.unwish(ME, CAMPAIGN_ID);

        assertThat(response).isEqualTo(new WishStatusResponse(CAMPAIGN_ID, 0, false));
    }

    @Test
    @DisplayName("삭제된 행사는 찜할 수 없지만, 이미 한 찜은 해제할 수 있다")
    void blocksWishOnDeletedButAllowsUnwish() {
        Campaign campaign = givenCampaign(CampaignStatus.OPEN);
        service.wish(ME, CAMPAIGN_ID);
        set(campaign, "status", CampaignStatus.DELETED);

        assertThatThrownBy(() -> service.wish(OTHER, CAMPAIGN_ID))
                .isInstanceOf(CampaignApplicationException.class)
                .hasMessageContaining("삭제된 캠페인");
        assertThat(service.unwish(ME, CAMPAIGN_ID).wishCount()).isZero();
    }

    @Test
    @DisplayName("없는 행사는 404 다")
    void rejectsUnknownCampaign() {
        assertThatThrownBy(() -> service.wish(ME, 999L))
                .isInstanceOf(CampaignApplicationException.class);
        assertThatThrownBy(() -> service.getWishStatus(999L, ME))
                .isInstanceOf(CampaignApplicationException.class);
    }

    private Campaign givenCampaign(CampaignStatus status) {
        Campaign campaign = new Campaign(
                10L, "code", "행사", CampaignType.TICKET, 10, LocalDateTime.of(2026, 10, 1, 0, 0), null);
        set(campaign, "id", CAMPAIGN_ID);
        set(campaign, "status", status);
        campaignRepository.campaigns.put(CAMPAIGN_ID, campaign);
        return campaign;
    }

    private static void set(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no such field: " + fieldName);
    }

    /** (user_id, campaign_id) 유니크 제약을 흉내낸다. */
    private static final class FakeWishRepository implements WishRepository {

        private final List<Wish> wishes = new ArrayList<>();

        /** exists 는 false 라고 답했는데 그 사이 다른 요청이 먼저 넣은 상황. */
        boolean racingDuplicate;

        @Override
        public Wish save(Wish wish) {
            if (racingDuplicate) {
                racingDuplicate = false;
                wishes.add(wish);
                throw new DataIntegrityViolationException("uk_wish_user_campaign");
            }
            if (exists(wish.getUserId(), wish.getCampaignId())) {
                throw new DataIntegrityViolationException("uk_wish_user_campaign");
            }
            wishes.add(wish);
            return wish;
        }

        @Override
        public boolean exists(Long userId, Long campaignId) {
            return !racingDuplicate && wishes.stream().anyMatch(wish ->
                    wish.getUserId().equals(userId) && wish.getCampaignId().equals(campaignId));
        }

        @Override
        public int delete(Long userId, Long campaignId) {
            int before = wishes.size();
            wishes.removeIf(wish ->
                    wish.getUserId().equals(userId) && wish.getCampaignId().equals(campaignId));
            return before - wishes.size();
        }

        @Override
        public long countByCampaignId(Long campaignId) {
            return wishes.stream().filter(wish -> wish.getCampaignId().equals(campaignId)).count();
        }

        @Override
        public List<Wish> findAllByUserIdLatestFirst(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findUserIdsByCampaignId(Long campaignId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteAllByUserId(Long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeCampaignRepository implements CampaignRepository {

        private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();

        @Override
        public Optional<Campaign> findById(Long campaignId) {
            return Optional.ofNullable(campaigns.get(campaignId));
        }

        @Override
        public Campaign save(Campaign campaign) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Campaign> findByShortCode(String shortCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByShortCode(String shortCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllOwnedBy(Long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllLiveOwnedBy(Long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllByIdIn(Collection<Long> campaignIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllByStatusAndOpenAtLessThanEqual(
                CampaignStatus status, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> searchLiveByTitle(String keyword, Long cursor, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findLivePageOwnedBy(Long ownerId, Long cursor, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllLiveByEventAtBetween(LocalDateTime from, LocalDateTime to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markDeleted(Long campaignId) {
            throw new UnsupportedOperationException();
        }
    }
}
