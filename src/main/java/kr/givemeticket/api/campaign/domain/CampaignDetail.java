package kr.givemeticket.api.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 행사 안내 정보. 신청·재고 로직에는 관여하지 않고 화면에만 쓰인다.
 *
 * <p>전체도, 각 필드도 선택이다. 별도 테이블로 빼지 않고 embed 하는 이유는
 * 캠페인 없이 존재할 수 없고 항상 캠페인과 같이 읽히기 때문이다. 조인이 늘 뿐 얻는 게 없다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignDetail {

    public static final int MAX_CONTENT_LENGTH = 5_000;

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "detail_content")
    private String content;

    /** 행사 시작 일시. 신청 오픈 시각({@code openAt})과는 무관하다. */
    @Column(name = "detail_event_at")
    private LocalDateTime eventAt;

    @Column(name = "detail_event_end_at")
    private LocalDateTime eventEndAt;

    /** 장소명. 예: 올림픽공원 체조경기장 */
    @Column(name = "detail_location", length = 200)
    private String location;

    /** 상세 주소. 장소명만으로 찾아갈 수 없는 경우가 많다. */
    @Column(name = "detail_address", length = 300)
    private String address;

    /** 포스터·썸네일 */
    @Column(name = "detail_image_url", length = 500)
    private String imageUrl;

    /** 주최자 문의처. 형식을 강제하지 않는다 (전화·이메일·오픈채팅 링크 등) */
    @Column(name = "detail_contact", length = 200)
    private String contact;

    /** 참가비(원). 결제 금액이 아니라 화면 안내용이다. */
    @Column(name = "detail_price")
    private Integer price;

    public CampaignDetail(
            String content,
            LocalDateTime eventAt,
            LocalDateTime eventEndAt,
            String location,
            String address,
            String imageUrl,
            String contact,
            Integer price
    ) {
        this.content = content;
        this.eventAt = eventAt;
        this.eventEndAt = eventEndAt;
        this.location = location;
        this.address = address;
        this.imageUrl = imageUrl;
        this.contact = contact;
        this.price = price;
    }

    /**
     * 모든 필드가 비었는지. 이 경우 응답에서 detail 자체를 내리지 않는다.
     */
    public boolean isEmpty() {
        return content == null && eventAt == null && eventEndAt == null
                && location == null && address == null
                && imageUrl == null && contact == null && price == null;
    }
}
