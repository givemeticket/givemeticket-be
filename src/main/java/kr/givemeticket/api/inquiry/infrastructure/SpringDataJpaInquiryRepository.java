package kr.givemeticket.api.inquiry.infrastructure;

import java.util.List;
import kr.givemeticket.api.inquiry.domain.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaInquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findAllByOrderByIdDesc();
}
