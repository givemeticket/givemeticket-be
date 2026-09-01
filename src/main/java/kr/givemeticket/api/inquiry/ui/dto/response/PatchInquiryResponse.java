package kr.givemeticket.api.inquiry.ui.dto.response;

import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;

public record PatchInquiryResponse(
        InquiryResponsePart inquiry
) {

    public static PatchInquiryResponse from(InquiryResponse response) {
        return new PatchInquiryResponse(InquiryResponsePart.from(response));
    }
}
