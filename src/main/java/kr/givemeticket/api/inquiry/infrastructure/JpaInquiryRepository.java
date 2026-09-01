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

    /**
     * 곧바로 flush 한다. 감사 컬럼(createdAt·updatedAt)은 flush 때 채워지는데,
     * 응답을 만드는 시점은 커밋보다 앞이라 flush 하지 않으면 옛 값이 나간다.
     */
    @Override
    public Inquiry save(Inquiry inquiry) {
        return springDataJpaInquiryRepository.saveAndFlush(inquiry);
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
