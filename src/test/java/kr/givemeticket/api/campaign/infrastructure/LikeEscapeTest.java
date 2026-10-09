package kr.givemeticket.api.campaign.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색어의 %, _ 는 LIKE 에서 와일드카드다. "100%" 로 찾았는데 "100" 으로 시작하는 행사가
 * 전부 나오면 안 된다. 쿼리의 ESCAPE '!' 와 짝이 맞아야 한다.
 */
class LikeEscapeTest {

    @Test
    @DisplayName("와일드카드와 이스케이프 문자를 글자로 바꾼다")
    void escapesWildcards() {
        assertThat(CampaignSearchQuery.escapeLike("100%_할인!")).isEqualTo("100!%!_할인!!");
    }

    @Test
    @DisplayName("평범한 검색어는 그대로다")
    void keepsPlainKeyword() {
        assertThat(CampaignSearchQuery.escapeLike("IU 콘서트")).isEqualTo("IU 콘서트");
    }
}
