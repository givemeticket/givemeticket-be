package kr.givemeticket.api.inquiry.ui.dto.response;

import java.util.List;
import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;

/**
 * 최신 문의부터 담긴다.
 *
 * @param totalCount 목록의 길이. 프론트가 세지 않아도 되도록 함께 내려준다
 */
public record GetInquiriesResponse(
        int totalCount,
        List<InquiryResponsePart> inquiries
) {

    public static GetInquiriesResponse from(List<InquiryResponse> responses) {
        return new GetInquiriesResponse(
                responses.size(),
                responses.stream().map(InquiryResponsePart::from).toList());
    }
}
