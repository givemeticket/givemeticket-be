package kr.givemeticket.api.inquiry.domain;

import java.util.List;
import java.util.Optional;

public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(Long inquiryId);

    /** 최신 문의부터. 아직 운영자 한 명이 보는 화면이라 페이징은 두지 않았다. */
    List<Inquiry> findAllLatestFirst();

    void delete(Inquiry inquiry);
}
