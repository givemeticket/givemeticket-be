package kr.givemeticket.api.inquiry.ui.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryCreateRequest;
import kr.givemeticket.api.inquiry.domain.Inquiry;

/**
 * email 은 선택이다. 답이 필요 없는 제보도 문의로 받는다.
 */
public record PostInquiryRequest(
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = Inquiry.TITLE_MAX_LENGTH, message = "title은 100자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "content는 필수입니다.")
        @Size(max = Inquiry.CONTENT_MAX_LENGTH, message = "content는 2000자를 넘을 수 없습니다.")
        String content,

        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = Inquiry.EMAIL_MAX_LENGTH, message = "email은 255자를 넘을 수 없습니다.")
        String email
) {

    public InquiryCreateRequest toInquiryCreateRequest() {
        return new InquiryCreateRequest(title, content, email);
    }
}
