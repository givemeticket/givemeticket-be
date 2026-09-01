package kr.givemeticket.api.inquiry.application.dto.request;

/** null 인 항목은 그대로 둔다. */
public record InquiryUpdateRequest(
        String title,
        String content,
        String email
) {
}
