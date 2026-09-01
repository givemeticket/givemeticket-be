package kr.givemeticket.api.inquiry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import kr.givemeticket.api.global.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 운영자에게 남기는 문의 한 건.
 *
 * <p>작성자를 userId 로 묶지 않는다. 로그인하지 않은 사람도 문의는 넣을 수 있어야 하고,
 * 답을 어디로 보낼지는 {@code email} 하나면 된다.
 */
@Getter
@Entity
@Table(name = "inquiry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int CONTENT_MAX_LENGTH = 2000;
    public static final int EMAIL_MAX_LENGTH = 255;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    /**
     * 본문. 길이 상한은 검증에서 막지만, 컬럼은 TEXT 로 잡아 상한을 나중에 올려도
     * 스키마를 건드리지 않게 한다.
     */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    /** 답을 보낼 곳. 답을 받지 않겠다면 비워둘 수 있다. */
    @Column(name = "email", length = EMAIL_MAX_LENGTH)
    private String email;

    public Inquiry(String title, String content, String email) {
        this.title = title;
        this.content = content;
        this.email = email;
    }

    /**
     * 보낸 값만 바꾼다. null 은 "안 바꾸겠다"는 뜻이다 — 이메일을 지우고 싶으면
     * 빈 문자열을 보내면 된다.
     */
    public void update(String title, String content, String email) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (email != null) {
            this.email = email.isBlank() ? null : email;
        }
    }
}
