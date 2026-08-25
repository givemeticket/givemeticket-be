package kr.givemeticket.api.campaign.domain;

import java.time.LocalDateTime;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 캐시 항목 하나가 힙을 얼마나 차지하는지 재는 도구. 단언이 없는 측정용이라 평소에는 꺼 둔다.
 *
 * <p>{@code campaign.cache.local.max-size} 를 정할 때 쓴 숫자가 여기서 나왔다.
 * 스냅샷에 필드가 늘거나 안내문 상한이 바뀌면 다시 돌려서 max-size 를 재검토한다.
 *
 * <pre>./gradlew test --tests '*SnapshotFootprintTest*' -i</pre>
 */
@Disabled("측정 도구. 필요할 때만 수동으로 돌린다")
class SnapshotFootprintTest {

    private static final int N = 20_000;

    @Test
    void measure() {
        for (int chars : new int[]{300, 800, 1500, 5000}) {
            System.out.printf("안내문 %5d자 -> 항목당 약 %6.2f KB%n", chars, measureKb(chars));
        }
    }

    private double measureKb(int contentChars) {
        // 실제 운영과 같은 조건으로 잰다. Caffeine 내부 노드 오버헤드까지 포함된 값이다.
        Cache<String, CampaignSnapshot> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(N)
                .build();
        settle();
        long before = used();
        for (int i = 0; i < N; i++) {
            CampaignSnapshot v = snapshot(i, contentChars);
            cache.put(v.shortCode(), v);
        }
        cache.cleanUp();
        settle();
        long after = used();
        if (cache.estimatedSize() < N / 2) {
            throw new IllegalStateException("항목이 축출됐다: " + cache.estimatedSize());
        }
        return (after - before) / 1024.0 / N;
    }

    private static void settle() {
        for (int i = 0; i < 4; i++) {
            System.gc();
            try { Thread.sleep(120); } catch (InterruptedException ignored) { }
        }
    }

    private static long used() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    /** 실제 행사처럼 한글 안내문 + 장소/주소/이미지/연락처를 채운다. */
    private static CampaignSnapshot snapshot(int i, int contentChars) {
        String paragraph = "공연 30분 전부터 입장 가능합니다. 티켓은 예매자 본인 확인 후 수령하실 수 있으며, "
                + "신분증을 반드시 지참해 주세요. 공연장 내 음식물 반입은 제한됩니다. ";
        StringBuilder sb = new StringBuilder(contentChars);
        while (sb.length() < contentChars) {
            sb.append(paragraph);
        }
        String content = sb.substring(0, contentChars);

        return new CampaignSnapshot(
                (long) i, 1L, "code" + i, "2026 서울재즈페스티벌 얼리버드 " + i,
                CampaignType.TICKET, 500,
                LocalDateTime.of(2026, 9, 1, 12, 0), true, CampaignStatus.OPEN,
                new CampaignSnapshot.Detail(
                        content,
                        LocalDateTime.of(2026, 9, 10, 19, 0),
                        LocalDateTime.of(2026, 9, 10, 22, 0),
                        "올림픽공원 88잔디마당",
                        "서울특별시 송파구 올림픽로 424 올림픽공원",
                        "https://cdn.givemeticket.site/posters/" + i + ".png",
                        "010-1234-5678",
                        99000));
    }
}
