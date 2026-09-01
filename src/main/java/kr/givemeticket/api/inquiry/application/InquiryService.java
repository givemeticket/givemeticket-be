package kr.givemeticket.api.inquiry.application;

import java.util.List;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryCreateRequest;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryUpdateRequest;
import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;
import kr.givemeticket.api.inquiry.domain.Inquiry;
import kr.givemeticket.api.inquiry.domain.InquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 운영자에게 남기는 문의.
 *
 * <p><b>인증이 없다.</b> 관리자 개념이 아직 없어서 조회·수정·삭제도 누구나 부를 수 있다.
 * 그래서 문의 본문에 개인정보를 담지 않도록 API 문서에서 못 박아 두었고,
 * 관리자 인증이 생기면 조회·수정·삭제 셋을 그 뒤로 옮겨야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    @Transactional
    public InquiryResponse create(InquiryCreateRequest request) {
        Inquiry inquiry = inquiryRepository.save(
                new Inquiry(request.title(), request.content(), normalize(request.email())));

        log.info("inquiry created: inquiryId={}", inquiry.getId());
        return InquiryResponse.from(inquiry);
    }

    public List<InquiryResponse> getInquiries() {
        return inquiryRepository.findAllLatestFirst().stream()
                .map(InquiryResponse::from)
                .toList();
    }

    public InquiryResponse getInquiry(Long inquiryId) {
        return InquiryResponse.from(findInquiry(inquiryId));
    }

    @Transactional
    public InquiryResponse update(Long inquiryId, InquiryUpdateRequest request) {
        Inquiry inquiry = findInquiry(inquiryId);
        inquiry.update(request.title(), request.content(), request.email());

        return InquiryResponse.from(inquiry);
    }

    @Transactional
    public void delete(Long inquiryId) {
        inquiryRepository.delete(findInquiry(inquiryId));

        log.info("inquiry deleted: inquiryId={}", inquiryId);
    }

    private Inquiry findInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(InquiryApplicationException::notFound);
    }

    /** 폼이 빈 칸을 빈 문자열로 보내온다. 저장하기 전에 "없음"으로 맞춘다. */
    private static String normalize(String email) {
        return (email == null || email.isBlank()) ? null : email.trim();
    }
}
