package kr.givemeticket.api.inquiry.ui.dto.response;

import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;

public record GetInquiryResponse(
        InquiryResponsePart inquiry
) {

    public static GetInquiryResponse from(InquiryResponse response) {
        return new GetInquiryResponse(InquiryResponsePart.from(response));
    }
}
