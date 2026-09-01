package kr.givemeticket.api.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryCreateRequest;
import kr.givemeticket.api.inquiry.application.dto.request.InquiryUpdateRequest;
import kr.givemeticket.api.inquiry.application.dto.response.InquiryResponse;
import kr.givemeticket.api.inquiry.domain.Inquiry;
import kr.givemeticket.api.inquiry.domain.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 문의는 인증이 없어 규칙이랄 게 거의 없다. 고정할 것은 셋이다 —
 * 목록이 <b>최신순</b>인가, 수정이 <b>보낸 항목만</b> 바꾸는가, 없는 번호가 404 인가.
 */
class InquiryCrudTest {

    private final FakeInquiryRepository repository = new FakeInquiryRepository();
    private final InquiryService service = new InquiryService(repository);

    @Test
    @DisplayName("문의를 등록하면 접수 번호가 나온다")
    void createsInquiry() {
        InquiryResponse response = service.create(
                new InquiryCreateRequest("로그인이 안 돼요", "카카오로 들어가면 흰 화면입니다", "me@example.com"));

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("로그인이 안 돼요");
        assertThat(response.email()).isEqualTo("me@example.com");
    }

    @Test
    @DisplayName("이메일은 없어도 된다 — 빈 칸은 없음으로 저장한다")
    void allowsMissingEmail() {
        assertThat(service.create(new InquiryCreateRequest("제목", "본문", "   ")).email()).isNull();
        assertThat(service.create(new InquiryCreateRequest("제목", "본문", null)).email()).isNull();
    }

    @Test
    @DisplayName("목록은 최신 문의부터 내려간다")
    void listsLatestFirst() {
        service.create(new InquiryCreateRequest("먼저 온 문의", "본문", null));
        service.create(new InquiryCreateRequest("나중에 온 문의", "본문", null));

        assertThat(service.getInquiries()).extracting(InquiryResponse::title)
                .containsExactly("나중에 온 문의", "먼저 온 문의");
    }

    @Test
    @DisplayName("보낸 항목만 바뀐다")
    void updatesOnlyGivenFields() {
        Long id = service.create(new InquiryCreateRequest("제목", "본문", "me@example.com")).id();

        InquiryResponse updated = service.update(id, new InquiryUpdateRequest(null, "고친 본문", null));

        assertThat(updated.title()).isEqualTo("제목");
        assertThat(updated.content()).isEqualTo("고친 본문");
        assertThat(updated.email()).isEqualTo("me@example.com");
    }

    @Test
    @DisplayName("이메일은 빈 문자열로 지운다 — null 은 안 바꾸겠다는 뜻이라 쓸 수 없다")
    void clearsEmailWithBlank() {
        Long id = service.create(new InquiryCreateRequest("제목", "본문", "me@example.com")).id();

        assertThat(service.update(id, new InquiryUpdateRequest(null, null, "")).email()).isNull();
    }

    @Test
    @DisplayName("삭제하면 조회되지 않는다")
    void deletesInquiry() {
        Long id = service.create(new InquiryCreateRequest("제목", "본문", null)).id();

        service.delete(id);

        assertThat(service.getInquiries()).isEmpty();
        assertThatThrownBy(() -> service.getInquiry(id))
                .isInstanceOf(InquiryApplicationException.class);
    }

    @Test
    @DisplayName("없는 번호는 조회·수정·삭제 모두 404 다")
    void rejectsUnknownId() {
        assertThatThrownBy(() -> service.getInquiry(404L))
                .isInstanceOf(InquiryApplicationException.class);
        assertThatThrownBy(() -> service.update(404L, new InquiryUpdateRequest("제목", null, null)))
                .isInstanceOf(InquiryApplicationException.class);
        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(InquiryApplicationException.class);
    }

    /** id 를 대신 채워 주는 것 말고는 하는 일이 없다. */
    private static final class FakeInquiryRepository implements InquiryRepository {

        private final Map<Long, Inquiry> rows = new LinkedHashMap<>();
        private long sequence = 0L;

        @Override
        public Inquiry save(Inquiry inquiry) {
            if (inquiry.getId() == null) {
                set(inquiry, "id", ++sequence);
            }
            rows.put(inquiry.getId(), inquiry);
            return inquiry;
        }

        @Override
        public Optional<Inquiry> findById(Long inquiryId) {
            return Optional.ofNullable(rows.get(inquiryId));
        }

        @Override
        public List<Inquiry> findAllLatestFirst() {
            List<Inquiry> sorted = new ArrayList<>(rows.values());
            sorted.sort(Comparator.comparing(Inquiry::getId).reversed());
            return sorted;
        }

        @Override
        public void delete(Inquiry inquiry) {
            rows.remove(inquiry.getId());
        }

        /** id 는 BaseEntity 에 있어 상위 클래스까지 올라가며 찾는다. */
        private static void set(Object target, String field, Object value) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    // 상위 클래스에서 계속 찾는다.
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            throw new IllegalStateException("필드가 없다: " + field);
        }
    }
}
