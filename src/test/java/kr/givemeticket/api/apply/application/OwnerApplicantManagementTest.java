package kr.givemeticket.api.apply.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.givemeticket.api.apply.application.dto.response.ApplicantResponse;
import kr.givemeticket.api.apply.application.dto.response.ApplicationResponse;
import kr.givemeticket.api.apply.domain.Application;
import kr.givemeticket.api.apply.domain.ApplicationStatus;
import kr.givemeticket.api.apply.domain.FailureReason;
import kr.givemeticket.api.campaign.application.CampaignApplicationException;
import kr.givemeticket.api.campaign.domain.Campaign;
import kr.givemeticket.api.campaign.domain.CampaignRepository;
import kr.givemeticket.api.campaign.domain.CampaignStatus;
import kr.givemeticket.api.campaign.domain.CampaignType;
import kr.givemeticket.api.campaign.domain.StockDecreaseResult;
import kr.givemeticket.api.campaign.domain.StockRepository;
import kr.givemeticket.api.login.domain.Provider;
import kr.givemeticket.api.user.application.UserService;
import kr.givemeticket.api.user.domain.User;
import kr.givemeticket.api.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주최자가 신청자를 들여다보고 내보내는 경로.
 *
 * <p>고정하는 것은 셋이다 — 목록이 <b>신청한 순서</b>로 나오는가, 남의 행사를 건드릴 수
 * 없는가, 그리고 내보낸 자리가 <b>재고로 돌아오는가</b>. 마지막을 놓치면 주최자가
 * 취소할수록 앉을 수 없는 자리만 늘어난다.
 */
class OwnerApplicantManagementTest {

    private static final Long CAMPAIGN_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final int TOTAL_STOCK = 10;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 29, 12, 0);

    private final FakeApplicationRepository applicationRepository = new FakeApplicationRepository();
    private final FakeCampaignRepository campaignRepository = new FakeCampaignRepository();
    private final FakeSeatRepository stockRepository = new FakeSeatRepository();
    private final FakeUserRepository userRepository = new FakeUserRepository();

    private final ApplicationService service = new ApplicationService(
            applicationRepository,
            new UserService(userRepository, null),
            new ApplicationPersister(applicationRepository),
            campaignRepository,
            null,
            stockRepository,
            null,
            null,
            null);

    @Test
    @DisplayName("신청자 목록은 신청한 순서대로 내려간다")
    void listsApplicantsInAppliedOrder() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "늦게 온 사람", BASE.plusMinutes(5));
        givenApplicant(102L, 8L, "먼저 온 사람", BASE);

        List<ApplicantResponse> applicants = service.getApplicants(CAMPAIGN_ID, OWNER_ID);

        assertThat(applicants).extracting(ApplicantResponse::userId).containsExactly(8L, 7L);
        assertThat(applicants).extracting(ApplicantResponse::appliedAt)
                .containsExactly(BASE, BASE.plusMinutes(5));
    }

    @Test
    @DisplayName("목록에 신청자 정보가 함께 담긴다")
    void listsApplicantProfiles() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "민기", BASE);

        List<ApplicantResponse> applicants = service.getApplicants(CAMPAIGN_ID, OWNER_ID);

        assertThat(applicants).singleElement()
                .satisfies(applicant -> {
                    assertThat(applicant.applicationId()).isEqualTo(101L);
                    assertThat(applicant.nickname()).isEqualTo("민기");
                    assertThat(applicant.status()).isEqualTo(ApplicationStatus.CONFIRMED);
                });
    }

    @Test
    @DisplayName("사용자를 찾지 못해도 목록에서 빠지지 않는다")
    void keepsApplicantWithoutUserRow() {
        givenCampaign(CampaignStatus.OPEN);
        applicationRepository.put(Application.confirmed(101L, CAMPAIGN_ID, 7L, BASE));

        List<ApplicantResponse> applicants = service.getApplicants(CAMPAIGN_ID, OWNER_ID);

        assertThat(applicants).singleElement()
                .satisfies(applicant -> {
                    assertThat(applicant.userId()).isEqualTo(7L);
                    assertThat(applicant.nickname()).isNull();
                });
    }

    @Test
    @DisplayName("취소된 신청은 목록에 나오지 않는다")
    void excludesCancelledApplications() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "남은 사람", BASE);
        givenApplicant(102L, 8L, "나간 사람", BASE.plusMinutes(1));
        applicationRepository.cancelWithReason(
                102L, ApplicationStatus.active(), FailureReason.CANCELLED_BY_OWNER);

        List<ApplicantResponse> applicants = service.getApplicants(CAMPAIGN_ID, OWNER_ID);

        assertThat(applicants).extracting(ApplicantResponse::userId).containsExactly(7L);
    }

    @Test
    @DisplayName("남의 행사의 신청자는 볼 수 없다")
    void rejectsNonOwnerListing() {
        givenCampaign(CampaignStatus.OPEN);

        assertThatThrownBy(() -> service.getApplicants(CAMPAIGN_ID, OTHER_USER_ID))
                .isInstanceOf(CampaignApplicationException.class)
                .hasMessageContaining("본인이 만든 캠페인");
    }

    @Test
    @DisplayName("삭제된 행사의 신청자는 볼 수 없다")
    void rejectsDeletedCampaignListing() {
        givenCampaign(CampaignStatus.DELETED);

        assertThatThrownBy(() -> service.getApplicants(CAMPAIGN_ID, OWNER_ID))
                .isInstanceOf(CampaignApplicationException.class)
                .hasMessageContaining("삭제된 캠페인");
    }

    @Test
    @DisplayName("주최자가 내보내면 사유가 남고 자리는 재고로 돌아온다")
    void cancelsApplicantAndRestoresStock() {
        givenCampaign(CampaignStatus.OPEN);
        Application application = givenApplicant(101L, 7L, "민기", BASE);
        stockRepository.reserve(CAMPAIGN_ID, 7L);

        ApplicationResponse response = service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(response.failureReason()).isEqualTo(FailureReason.CANCELLED_BY_OWNER);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(stockRepository.stock.get(CAMPAIGN_ID)).isEqualTo(TOTAL_STOCK);
    }

    @Test
    @DisplayName("내보낸 사람은 다시 신청할 수 있다 — 차단이 아니라 취소다")
    void letsCancelledApplicantApplyAgain() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "민기", BASE);
        stockRepository.reserve(CAMPAIGN_ID, 7L);

        service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID);

        assertThat(stockRepository.applicants.get(CAMPAIGN_ID)).doesNotContain(7L);
    }

    @Test
    @DisplayName("이미 취소된 신청은 다시 내보낼 수 없다")
    void rejectsAlreadyCancelled() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "민기", BASE);
        stockRepository.reserve(CAMPAIGN_ID, 7L);
        service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID);

        assertThatThrownBy(() -> service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID))
                .isInstanceOf(ApplyApplicationException.class);
        // 두 번 눌러도 재고가 정원을 넘지 않는다.
        assertThat(stockRepository.stock.get(CAMPAIGN_ID)).isEqualTo(TOTAL_STOCK);
    }

    @Test
    @DisplayName("다른 행사의 신청 번호로는 내보낼 수 없다")
    void rejectsApplicationOfAnotherCampaign() {
        givenCampaign(CampaignStatus.OPEN);
        applicationRepository.put(Application.confirmed(101L, 2L, 7L, BASE));

        assertThatThrownBy(() -> service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID))
                .isInstanceOf(ApplyApplicationException.class)
                .hasMessageContaining("신청 내역을 찾을 수 없");
    }

    @Test
    @DisplayName("남의 행사의 신청자는 내보낼 수 없다")
    void rejectsNonOwnerCancel() {
        givenCampaign(CampaignStatus.OPEN);
        givenApplicant(101L, 7L, "민기", BASE);

        assertThatThrownBy(() -> service.cancelByOwner(CAMPAIGN_ID, 101L, OTHER_USER_ID))
                .isInstanceOf(CampaignApplicationException.class);
        assertThat(applicationRepository.findById(101L).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("종료된 행사에서도 내보낼 수 있다 — 확정 신청이 남아 있기 때문이다")
    void cancelsOnClosedCampaign() {
        givenCampaign(CampaignStatus.CLOSED);
        givenApplicant(101L, 7L, "민기", BASE);
        stockRepository.reserve(CAMPAIGN_ID, 7L);

        ApplicationResponse response = service.cancelByOwner(CAMPAIGN_ID, 101L, OWNER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELLED);
    }

    private void givenCampaign(CampaignStatus status) {
        Campaign campaign = new Campaign(OWNER_ID, "code", "행사", CampaignType.TICKET,
                TOTAL_STOCK, BASE.minusDays(1), null);
        set(campaign, "id", CAMPAIGN_ID);
        set(campaign, "status", status);
        campaignRepository.put(CAMPAIGN_ID, campaign);
        stockRepository.initialize(CAMPAIGN_ID, TOTAL_STOCK);
    }

    private Application givenApplicant(
            Long applicationId, Long userId, String nickname, LocalDateTime appliedAt) {
        Application application = Application.confirmed(applicationId, CAMPAIGN_ID, userId, appliedAt);
        applicationRepository.put(application);

        User user = new User(nickname, null, "provider-" + userId, Provider.KAKAO);
        set(user, "id", userId);
        userRepository.put(userId, user);
        return application;
    }

    private static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no such field: " + field);
    }

    /** 재고와 신청자 집합을 함께 다루는 Lua 스크립트의 규칙을 그대로 흉내낸다. */
    private static final class FakeSeatRepository implements StockRepository {
        final Map<Long, Long> stock = new LinkedHashMap<>();
        final Map<Long, Set<Long>> applicants = new HashMap<>();

        /** 신청 한 건이 이미 잡아둔 자리를 만든다. */
        void reserve(Long campaignId, Long userId) {
            applicants.computeIfAbsent(campaignId, k -> new HashSet<>()).add(userId);
            stock.computeIfPresent(campaignId, (k, remaining) -> remaining - 1);
        }

        @Override
        public void restore(Long campaignId, Long userId, int upperBound) {
            applicants.getOrDefault(campaignId, new HashSet<>()).remove(userId);
            Long current = stock.get(campaignId);
            if (current != null && current < upperBound) {
                stock.put(campaignId, current + 1);
            }
        }

        @Override
        public void initialize(Long campaignId, int totalStock) {
            stock.put(campaignId, (long) totalStock);
        }

        @Override
        public Long getRemaining(Long campaignId) {
            return stock.get(campaignId);
        }

        @Override
        public StockDecreaseResult decrease(Long campaignId, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void increaseBy(Long campaignId, int delta) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Long, Long> getRemainingAll(Collection<Long> campaignIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Long campaignId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeCampaignRepository implements CampaignRepository {
        private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();

        void put(Long campaignId, Campaign campaign) {
            campaigns.put(campaignId, campaign);
        }

        @Override
        public Optional<Campaign> findById(Long campaignId) {
            return Optional.ofNullable(campaigns.get(campaignId));
        }

        @Override
        public Optional<Campaign> findByShortCode(String shortCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Campaign> findAllOwnedBy(Long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Campaign save(Campaign campaign) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByShortCode(String shortCode) {
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
        public int markDeleted(Long campaignId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeUserRepository implements UserRepository {
        private final Map<Long, User> users = new LinkedHashMap<>();

        void put(Long userId, User user) {
            users.put(userId, user);
        }

        @Override
        public Optional<User> findById(Long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public List<User> findAllByIdIn(Collection<Long> userIds) {
            return userIds.stream().map(users::get).filter(user -> user != null).toList();
        }

        @Override
        public Optional<User> findByProviderIdAndProvider(String providerId, Provider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User save(User user) {
            throw new UnsupportedOperationException();
        }
    }
}
