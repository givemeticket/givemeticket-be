package kr.givemeticket.api.inquiry.application.dto.response;

import java.time.LocalDateTime;
import kr.givemeticket.api.inquiry.domain.Inquiry;

/**
 * @param email     답을 받을 곳. 남기지 않았으면 null
 * @param createdAt 문의를 남긴 시각
 * @param updatedAt 마지막으로 고친 시각. 고친 적이 없으면 createdAt 과 같다
 */
public record InquiryResponse(
        Long id,
        String title,
        String content,
        String email,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getEmail(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt());
    }
}
