package kr.givemeticket.api.inquiry.ui.dto.response;

import java.time.Instant;
import kr.givemeticket.api.global.time.Utc;
import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;

/**
 * 문의 한 건의 표현. 단건 조회와 목록이 같은 모양을 쓴다.
 *
 * @param createdAt UTC. 프론트가 로컬 시각으로 그릴 수 있도록 Z를 붙여 내려간다
 */
public record InquiryResponsePart(
        Long id,
        String title,
        String content,
        String email,
        Instant createdAt,
        Instant updatedAt
) {

    public static InquiryResponsePart from(InquiryResponse response) {
        return new InquiryResponsePart(
                response.id(),
                response.title(),
                response.content(),
                response.email(),
                Utc.toInstant(response.createdAt()),
                Utc.toInstant(response.updatedAt()));
    }
}
