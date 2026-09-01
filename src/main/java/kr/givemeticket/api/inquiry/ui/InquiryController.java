package kr.givemeticket.api.inquiry.ui;

import jakarta.validation.Valid;
import java.net.URI;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.inquiry.application.InquiryService;
import kr.givemeticket.api.inquiry.ui.apiSpec.InquiryApiSpec;
import kr.givemeticket.api.inquiry.ui.dto.request.PatchInquiryRequest;
import kr.givemeticket.api.inquiry.ui.dto.request.PostInquiryRequest;
import kr.givemeticket.api.inquiry.ui.dto.response.CreateInquiryResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.GetInquiriesResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.GetInquiryResponse;
import kr.givemeticket.api.inquiry.ui.dto.response.PatchInquiryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InquiryController implements InquiryApiSpec {

    private final InquiryService inquiryService;

    @Override
    @BusinessLogging("문의 등록")
    @PostMapping("inquiries")
    public ResponseEntity<CreateInquiryResponse> createInquiry(
            @Valid @RequestBody PostInquiryRequest request
    ) {
        CreateInquiryResponse createInquiryResponse = CreateInquiryResponse.from(
                inquiryService.create(request.toInquiryCreateRequest()));

        return ResponseEntity.created(URI.create("inquiries/" + createInquiryResponse.id()))
                .body(createInquiryResponse);
    }

    @Override
    @GetMapping("inquiries")
    public ResponseEntity<GetInquiriesResponse> readInquiries() {
        return ResponseEntity.ok(GetInquiriesResponse.from(inquiryService.getInquiries()));
    }

    @Override
    @GetMapping("inquiries/{inquiryId}")
    public ResponseEntity<GetInquiryResponse> readInquiry(
            @PathVariable("inquiryId") Long inquiryId
    ) {
        return ResponseEntity.ok(GetInquiryResponse.from(inquiryService.getInquiry(inquiryId)));
    }

    @Override
    @BusinessLogging("문의 수정")
    @PatchMapping("inquiries/{inquiryId}")
    public ResponseEntity<PatchInquiryResponse> updateInquiry(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody PatchInquiryRequest request
    ) {
        return ResponseEntity.ok(PatchInquiryResponse.from(
                inquiryService.update(inquiryId, request.toInquiryUpdateRequest())));
    }

    @Override
    @BusinessLogging("문의 삭제")
    @DeleteMapping("inquiries/{inquiryId}")
    public ResponseEntity<Void> deleteInquiry(@PathVariable("inquiryId") Long inquiryId) {
        inquiryService.delete(inquiryId);

        return ResponseEntity.noContent().build();
    }
}
