package kr.givemeticket.api.inquiry.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;

/**
 * 접수 결과. 방금 보낸 본문을 되돌려주지 않고 접수 번호와 시각만 내려준다.
 */
public record CreateInquiryResponse(
        Long id,
        String title,
        Instant createdAt
) {

    public static CreateInquiryResponse from(InquiryResponse response) {
        return new CreateInquiryResponse(
                response.id(), response.title(), Utc.toInstant(response.createdAt()));
    }
}
