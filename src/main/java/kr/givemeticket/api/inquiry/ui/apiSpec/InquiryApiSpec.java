package kr.givemeticket.api.inquiry.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.givemeticket.api.inquiry.ui.dto.request.PatchInquiryRequest;
import kr.givemeticket.api.inquiry.ui.dto.request.PostInquiryRequest;
import kr.givemeticket.api.inquiry.ui.dto.response.CreateInquiryResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.GetInquiriesResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.GetInquiryResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.PatchInquiryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 문의는 전부 인증 없이 호출됩니다. 관리자 인증이 아직 없어서 조회·수정·삭제도
 * 열려 있습니다 — 문의 본문에 개인정보를 담지 마세요.
 */
@Tag(name = "문의 API", description = "운영자에게 남기는 문의 API 명세입니다. 인증이 필요 없습니다.")
@SecurityRequirements
public interface InquiryApiSpec {

    @Operation(summary = "문의 등록",
            description = """
                    운영자에게 문의를 남깁니다. 로그인하지 않아도 됩니다.

                    - title 100자, content 2000자까지입니다. 둘 다 필수입니다
                    - email 은 선택입니다. 답을 받으려면 적어야 합니다
                    - 응답의 id 가 접수 번호입니다. 나중에 다시 열어보려면 들고 있어야 합니다
                    """)
    ResponseEntity<CreateInquiryResponse> createInquiry(
            @Valid @RequestBody PostInquiryRequest request);

    @Operation(summary = "문의 목록 조회",
            description = """
                    등록된 문의를 최신순으로 모두 내려줍니다.

                    - 운영자가 보는 화면입니다. 관리자 인증이 생기기 전까지는 누구나 부를 수 있습니다
                    - 페이징은 없습니다
                    """)
    ResponseEntity<GetInquiriesResponse> readInquiries();

    @Operation(summary = "문의 단건 조회",
            description = "없는 번호면 404 INQUIRY_NOT_FOUND 입니다.")
    ResponseEntity<GetInquiryResponse> readInquiry(
            @Parameter(description = "문의 ID", example = "1")
            @PathVariable("inquiryId") Long inquiryId
    );

    @Operation(summary = "문의 수정",
            description = """
                    보낸 항목만 바뀝니다. 안 바꿀 항목은 아예 빼거나 null 로 보내세요.

                    - title·content 는 값을 보냈다면 공백뿐이어선 안 됩니다
                    - email 만 빈 문자열("")을 허용합니다. 적어둔 주소를 지우는 방법입니다
                    - 없는 번호면 404 INQUIRY_NOT_FOUND 입니다
                    """)
    ResponseEntity<PatchInquiryResponse> updateInquiry(
            @Parameter(description = "문의 ID", example = "1")
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody PatchInquiryRequest request
    );

    @Operation(summary = "문의 삭제",
            description = """
                    문의를 지웁니다. 되돌릴 수 없습니다.

                    - 성공하면 204 이고 본문이 없습니다
                    - 없는 번호면 404 INQUIRY_NOT_FOUND 입니다
                    """)
    ResponseEntity<Void> deleteInquiry(
            @Parameter(description = "문의 ID", example = "1")
            @PathVariable("inquiryId") Long inquiryId
    );
}
