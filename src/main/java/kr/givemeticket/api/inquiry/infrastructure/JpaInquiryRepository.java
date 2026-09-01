package kr.givemeticket.api.inquiry.infrastructure;

import java.util.List;
import java.util.Optional;
import kr.givemeticket.api.inquiry.domain.Inquiry;
import kr.givemeticket.api.inquiry.domain.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaInquiryRepository implements InquiryRepository {

    private final SpringDataJpaInquiryRepository springDataJpaInquiryRepository;

    @Override
    public Inquiry save(Inquiry inquiry) {
        return springDataJpaInquiryRepository.save(inquiry);
    }

    @Override
    public Optional<Inquiry> findById(Long inquiryId) {
        return springDataJpaInquiryRepository.findById(inquiryId);
    }

    /**
     * 정렬 기준이 created_at 이 아니라 id 다. 같은 초에 들어온 문의끼리도 순서가 흔들리지 않고,
     * 값은 어차피 같은 방향으로 늘어난다.
     */
    @Override
    public List<Inquiry> findAllLatestFirst() {
        return springDataJpaInquiryRepository.findAllByOrderByIdDesc();
    }

    @Override
    public void delete(Inquiry inquiry) {
        springDataJpaInquiryRepository.delete(inquiry);
    }
}
