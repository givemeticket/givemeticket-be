package kr.givemeticket.api.inquiry.application.dto.request;

public record InquiryCreateRequest(
        String title,
        String content,
        String email
) {
}
