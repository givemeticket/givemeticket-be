package kr.givemeticket.api.inquiry.ui.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryUpdateRequest;
import kr.givemeticket.api.inquiry.domain.Inquiry;

/**
 * 전부 선택 항목이다. null 은 "안 바꾸겠다"는 뜻이라 {@code @NotBlank} 를 걸 수 없다 —
 * 대신 값을 보냈다면 공백뿐이어선 안 되게 막는다.
 *
 * <p>email 만 예외로 빈 문자열을 허용한다. 적어둔 주소를 지우는 유일한 방법이다.
 */
public record PatchInquiryRequest(
        @Pattern(regexp = ".*\\S.*", message = "title은 공백일 수 없습니다.")
        @Size(max = Inquiry.TITLE_MAX_LENGTH, message = "title은 100자를 넘을 수 없습니다.")
        String title,

        @Pattern(regexp = ".*\\S.*", message = "content는 공백일 수 없습니다.")
        @Size(max = Inquiry.CONTENT_MAX_LENGTH, message = "content는 2000자를 넘을 수 없습니다.")
        String content,

        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = Inquiry.EMAIL_MAX_LENGTH, message = "email은 255자를 넘을 수 없습니다.")
        String email
) {

    public InquiryUpdateRequest toInquiryUpdateRequest() {
        return new InquiryUpdateRequest(title, content, email);
    }
}
